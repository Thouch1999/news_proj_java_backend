package com.api.v1.newssystem.service.impl;

import com.api.v1.newssystem.common.constant.MessageConstant;
import com.api.v1.newssystem.common.exception.CustomException;
import com.api.v1.newssystem.model.enumerate.ActivityAction;
import com.api.v1.newssystem.model.enumerate.ActivityTargetType;
import com.api.v1.newssystem.service.ActivityLogService;
import com.api.v1.newssystem.service.LoginLockoutService;
import com.api.v1.newssystem.util.ActivityDetails;
import com.api.v1.newssystem.model.entity.AppUser;
import com.api.v1.newssystem.model.entity.Role;
import com.api.v1.newssystem.model.entity.UserRole;
import com.api.v1.newssystem.model.enumerate.Roles;
import com.api.v1.newssystem.model.request.AdminCreateUserRequest;
import com.api.v1.newssystem.model.request.AdminResetPasswordRequest;
import com.api.v1.newssystem.model.request.AppUserAddRequest;
import com.api.v1.newssystem.model.request.ChangeRoleRequest;
import com.api.v1.newssystem.model.request.TransferSuperAdminRequest;
import com.api.v1.newssystem.model.request.UpdateProfileRequest;
import com.api.v1.newssystem.model.response.AppUserDto;
import com.api.v1.newssystem.repository.AppUserRepository;
import com.api.v1.newssystem.repository.RoleRepository;
import com.api.v1.newssystem.repository.UserRoleRepository;
import com.api.v1.newssystem.security.CurrentUserService;
import com.api.v1.newssystem.service.AppUserService;
import com.api.v1.newssystem.service.AppUserTokenService;
import com.api.v1.newssystem.service.FileStorageService;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.modelmapper.ModelMapper;
import com.api.v1.newssystem.service.NotificationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@AllArgsConstructor
public class AppUserServiceImpl implements AppUserService {
    private static final int MAX_PAGE_SIZE = 100;

    private final AppUserRepository appUserRepository;
    private final AppUserTokenService tokenService;
    private final CurrentUserService currentUserService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final ModelMapper mapper;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final FileStorageService fileStorageService;
    private final ApplicationEventPublisher eventPublisher;
    private final ActivityLogService activityLogService;
    private final LoginLockoutService loginLockoutService;

    @Override
    @NullMarked
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return appUserRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(HttpStatus.NOT_FOUND, MessageConstant.NOT_FOUND.getDefaultMessage()));
    }

    @Override
    public AppUserDto current() {
        return mapper.map(currentUserService.getCurrentUser(), AppUserDto.class);
    }

    @Override
    @Transactional
    public void logout() {
        AppUser user = currentUserService.getCurrentUser();

        tokenService.findAllByUser(user)
                .ifPresent(tokenService::deleteAll);

        SecurityContextHolder.clearContext();
    }

    @Override
    public AppUserDto create(AppUserAddRequest request) {
        AppUser user = mapper.map(request, AppUser.class);
        user.setPassword(
                passwordEncoder.encode(
                        this.confirm(request.getPassword(), request.getConfirm())
                )
        );
        return mapper.map(appUserRepository.save(user), AppUserDto.class);
    }

    @Override
    public AppUserDto update(UpdateProfileRequest request) {
        AppUser user = findUser(currentUserService.getCurrentUser().getId());
        user.setUsername(request.getUsername());
        user.setFullName(request.getFullName());
        user.setAddress(request.getAddress());
        user.setPhone(request.getPhone());
        String previousAvatar = user.getAvatar();
        user.setAvatar(request.getAvatar());
        AppUserDto saved = mapper.map(appUserRepository.save(user), AppUserDto.class);
        if (previousAvatar != null && !previousAvatar.equals(request.getAvatar())) {
            fileStorageService.delete(previousAvatar); // ignored unless it is a file this server stored
        }
        return saved;
    }

    @Override
    @Transactional
    public AppUserDto updateAvatar(MultipartFile file) {
        AppUser user = findUser(currentUserService.getCurrentUser().getId());
        String previousAvatar = user.getAvatar();
        user.setAvatar(fileStorageService.store(file));
        AppUserDto saved = mapper.map(appUserRepository.save(user), AppUserDto.class);
        fileStorageService.delete(previousAvatar);
        return saved;
    }

    @Override
    @Transactional
    public AppUserDto removeAvatar() {
        AppUser user = findUser(currentUserService.getCurrentUser().getId());
        String previousAvatar = user.getAvatar();
        user.setAvatar(null);
        AppUserDto saved = mapper.map(appUserRepository.save(user), AppUserDto.class);
        fileStorageService.delete(previousAvatar);
        return saved;
    }

    @Override
    public Page<AppUserDto> findAll(String search, Roles role, Boolean active, Boolean emailVerified, int page, int size) {
        int pageIndex = Math.max(page, 1) - 1;
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        String term = (search == null || search.isBlank()) ? null : search.trim();

        return appUserRepository
                .search(term, role, active, emailVerified, PageRequest.of(pageIndex, pageSize, Sort.by("id").descending()))
                .map(user -> mapper.map(user, AppUserDto.class));
    }

    @Override
    public AppUserDto findById(Long id) {
        AppUser user = findUser(id);
        AppUserDto dto = mapper.map(user, AppUserDto.class);
        dto.setLocked(loginLockoutService.isLocked(user.getEmail()));
        return dto;
    }

    @Override
    @Transactional
    public AppUserDto unlock(Long id) {
        AppUser user = findUser(id);
        if (loginLockoutService.unlock(user.getEmail())) { // nothing to lift, nothing to log
            activityLogService.record(ActivityAction.ACCOUNT_UNLOCKED, ActivityTargetType.USER, user.getId(), label(user), null);
        }
        AppUserDto dto = mapper.map(user, AppUserDto.class);
        dto.setLocked(false);
        return dto;
    }

    @Override
    @Transactional
    public AppUserDto createStaff(AdminCreateUserRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        if (appUserRepository.existsByEmail(email)) {
            throw new CustomException(HttpStatus.CONFLICT, MessageConstant.EMAIL_ALREADY_EXISTS.getDefaultMessage());
        }
        // any of the four roles, as long as it exists in the roles table (an unknown name never gets here: the request cannot be read)
        if (request.getRole() == null || roleRepository.findByName(request.getRole().name()).isEmpty()) {
            throw new CustomException(HttpStatus.BAD_REQUEST, MessageConstant.INVALID_PARAMETER.getDefaultMessage());
        }

        if (request.getRole() == Roles.ADMIN) {
            requireSuperAdmin(currentUserService.getCurrentUser());
        }

        AppUser user = new AppUser();
        user.setUsername(request.getUsername());
        user.setFullName(request.getFullName());
        user.setEmail(email);
        user.setAddress(request.getAddress());
        user.setPhone(request.getPhone());
        user.setPassword(passwordEncoder.encode(this.confirm(request.getPassword(), request.getConfirm())));
        user.setRole(request.getRole());
        user.setActive(true);
        user.setEmailVerified(true);
        user = appUserRepository.save(user);

        syncUserRole(user);
        activityLogService.record(ActivityAction.USER_CREATED, ActivityTargetType.USER, user.getId(), label(user),
                ActivityDetails.of("role", user.getRole(), "adminRoleGranted", user.getRole() == Roles.ADMIN ? true : null));
        return mapper.map(user, AppUserDto.class);
    }

    @Override
    @Transactional
    public AppUserDto setActive(Long id, boolean active) {
        AppUser actor = currentUserService.getCurrentUser();
        if (!active && id.equals(actor.getId())) {
            throw actor.isSuperAdmin()
                    ? new CustomException(HttpStatus.FORBIDDEN, MessageConstant.SUPER_ADMIN_CANNOT_BE_DEACTIVATED.getDefaultMessage())
                    : new CustomException(HttpStatus.BAD_REQUEST, MessageConstant.CANNOT_DEACTIVATE_SELF.getDefaultMessage());
        }
        AppUser user = findUser(id);
        if (!active) {
            if (user.isSuperAdmin()) {
                throw new CustomException(HttpStatus.FORBIDDEN, MessageConstant.SUPER_ADMIN_CANNOT_BE_DEACTIVATED.getDefaultMessage());
            }
            if (user.getRole() == Roles.ADMIN) {
                requireSuperAdmin(actor);
                requireAnotherActiveAdmin(user);
            }
        }
        boolean changed = user.isActive() != active;
        user.setActive(active);
        user = appUserRepository.save(user);
        if (!active) {
            revokeRefreshTokens(user);
        }
        if (changed) {
            activityLogService.record(active ? ActivityAction.USER_ACTIVATED : ActivityAction.USER_DEACTIVATED,
                    ActivityTargetType.USER, user.getId(), label(user), null);
        }
        return mapper.map(user, AppUserDto.class);
    }

    @Override
    @Transactional
    public AppUserDto changeRole(Long id, ChangeRoleRequest request) {
        AppUser actor = currentUserService.getCurrentUser();
        if (id.equals(actor.getId()) && actor.isSuperAdmin()) {
            throw new CustomException(HttpStatus.FORBIDDEN, MessageConstant.SUPER_ADMIN_ROLE_PROTECTED.getDefaultMessage());
        }
        if (id.equals(actor.getId()) && request.getRole() != Roles.ADMIN) {
            throw new CustomException(HttpStatus.BAD_REQUEST, MessageConstant.CANNOT_REMOVE_OWN_ADMIN_ROLE.getDefaultMessage());
        }
        AppUser user = findUser(id);
        if (user.isSuperAdmin()) {
            throw new CustomException(HttpStatus.FORBIDDEN, MessageConstant.SUPER_ADMIN_ROLE_PROTECTED.getDefaultMessage());
        }
        Roles previous = user.getRole();
        boolean touchesAdminRole = (previous == Roles.ADMIN) != (request.getRole() == Roles.ADMIN);
        if (touchesAdminRole) {
            requireSuperAdmin(actor);
            if (previous == Roles.ADMIN) {
                requireAnotherActiveAdmin(user);
            }
        }
        user.setRole(request.getRole());
        user = appUserRepository.save(user);

        syncUserRole(user);
        if (previous != user.getRole()) {
            activityLogService.record(ActivityAction.USER_ROLES_CHANGED, ActivityTargetType.USER, user.getId(), label(user),
                    ActivityDetails.of("roles", ActivityDetails.change(List.of(previous.name()), List.of(user.getRole().name())),
                            "adminRoleGranted", touchesAdminRole && user.getRole() == Roles.ADMIN ? true : null,
                            "adminRoleRemoved", touchesAdminRole && previous == Roles.ADMIN ? true : null));
            // a user has one role: assigning a new one removes the old one
            eventPublisher.publishEvent(NotificationEvent.rolesChanged(actor, user,
                    List.of(user.getRole().name()), List.of(previous.name())));
        }
        return mapper.map(user, AppUserDto.class);
    }

    @Override
    @Transactional
    public void resetPassword(Long id, AdminResetPasswordRequest request) {
        AppUser actor = currentUserService.getCurrentUser();
        AppUser user = findUser(id);
        if (user.isSuperAdmin()) {
            throw new CustomException(HttpStatus.FORBIDDEN, MessageConstant.SUPER_ADMIN_PASSWORD_PROTECTED.getDefaultMessage());
        }
        if (user.getRole() == Roles.ADMIN && !id.equals(actor.getId())) {
            requireSuperAdmin(actor);
        }
        user.setPassword(passwordEncoder.encode(this.confirm(request.getPassword(), request.getConfirm())));
        appUserRepository.save(user);
        revokeRefreshTokens(user);
        activityLogService.record(ActivityAction.USER_PASSWORD_RESET, ActivityTargetType.USER, user.getId(), label(user), null); // never the password
    }

    @Override
    @Transactional
    public AppUserDto transferSuperAdmin(TransferSuperAdminRequest request) {
        AppUser actor = findUser(currentUserService.getCurrentUser().getId()); // fresh from the database, not the token's copy
        requireSuperAdmin(actor);
        if (!passwordEncoder.matches(request.getPassword(), actor.getPassword())) {
            throw new CustomException(HttpStatus.BAD_REQUEST, MessageConstant.CURRENT_PASSWORD_INCORRECT.getDefaultMessage());
        }
        AppUser target = findUser(request.getUserId());
        if (target.getId().equals(actor.getId()) || target.getRole() != Roles.ADMIN
                || !target.isActive() || !target.isEmailVerified()) {
            throw new CustomException(HttpStatus.BAD_REQUEST, MessageConstant.SUPER_ADMIN_TRANSFER_INVALID_TARGET.getDefaultMessage());
        }

        // the old mark is cleared and written first, so the unique index never sees two marks
        actor.setSuperAdmin(false);
        appUserRepository.saveAndFlush(actor);
        target.setSuperAdmin(true);
        target = appUserRepository.saveAndFlush(target);

        activityLogService.record(ActivityAction.SUPER_ADMIN_TRANSFERRED, ActivityTargetType.USER, target.getId(), label(target),
                ActivityDetails.of("from", label(actor), "fromId", actor.getId(), "to", label(target), "toId", target.getId()));
        return mapper.map(target, AppUserDto.class);
    }

    // only the super admin may give or remove the ADMIN role, create an admin, or deactivate / reset another admin
    private void requireSuperAdmin(AppUser actor) {
        if (!actor.isSuperAdmin()) {
            throw new CustomException(HttpStatus.FORBIDDEN, MessageConstant.SUPER_ADMIN_ONLY.getDefaultMessage());
        }
    }

    // the system keeps at least one active admin: `leaving` is about to stop being one
    private void requireAnotherActiveAdmin(AppUser leaving) {
        if (leaving.isActive() && appUserRepository.countByRoleAndActiveTrue(Roles.ADMIN) <= 1) {
            throw new CustomException(HttpStatus.CONFLICT, MessageConstant.LAST_ADMIN_REQUIRED.getDefaultMessage());
        }
    }

    // how the user is named in the activity log: the username, else the email
    private String label(AppUser user) {
        return user.getName() != null && !user.getName().isBlank() ? user.getName() : user.getEmail();
    }

    // keeps the user_roles table in line with AppUser.role: exactly one link, to the current role
    private void syncUserRole(AppUser user) {
        Role role = roleRepository.findByName(user.getRole().name())
                .orElseThrow(() -> new CustomException(HttpStatus.NOT_FOUND, MessageConstant.RESOURCE_NOT_FOUND.getDefaultMessage()));

        boolean linked = false;
        List<UserRole> links = userRoleRepository.findAllByUserId(user.getId());
        for (UserRole link : links) {
            if (link.getRole().getId().equals(role.getId())) {
                linked = true;
            } else {
                userRoleRepository.delete(link);
            }
        }
        if (!linked) {
            UserRole link = new UserRole();
            link.setUser(user);
            link.setRole(role);
            userRoleRepository.save(link);
        }
    }

    private void revokeRefreshTokens(AppUser user) {
        tokenService.findAllByUser(user).ifPresent(tokenService::deleteAll);
    }

    private AppUser findUser(Long id) {
        return appUserRepository.findById(id)
                .orElseThrow(() -> new CustomException(HttpStatus.NOT_FOUND, MessageConstant.USER_NOT_FOUND.getDefaultMessage()));
    }

    private String confirm(String password, String confirm) {
        if (!password.equals(confirm)) {
            throw new CustomException(HttpStatus.BAD_REQUEST, MessageConstant.CONFIRM_PASSWORD_NOT_MATCH.getDefaultMessage());
        }
        return password;
    }
}
