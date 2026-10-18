package com.api.v1.bearerauth.service.impl;

import com.api.v1.bearerauth.common.constant.MessageConstant;
import com.api.v1.bearerauth.common.exception.CustomException;
import com.api.v1.bearerauth.model.entity.AppUser;
import com.api.v1.bearerauth.model.entity.Role;
import com.api.v1.bearerauth.model.entity.UserRole;
import com.api.v1.bearerauth.model.enumerate.Roles;
import com.api.v1.bearerauth.model.request.AdminCreateUserRequest;
import com.api.v1.bearerauth.model.request.AdminResetPasswordRequest;
import com.api.v1.bearerauth.model.request.AppUserAddRequest;
import com.api.v1.bearerauth.model.request.ChangeRoleRequest;
import com.api.v1.bearerauth.model.request.UpdateProfileRequest;
import com.api.v1.bearerauth.model.response.AppUserDto;
import com.api.v1.bearerauth.repository.AppUserRepository;
import com.api.v1.bearerauth.repository.RoleRepository;
import com.api.v1.bearerauth.repository.UserRoleRepository;
import com.api.v1.bearerauth.security.CurrentUserService;
import com.api.v1.bearerauth.service.AppUserService;
import com.api.v1.bearerauth.service.AppUserTokenService;
import com.api.v1.bearerauth.service.FileStorageService;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.modelmapper.ModelMapper;
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
        return mapper.map(findUser(id), AppUserDto.class);
    }

    @Override
    @Transactional
    public AppUserDto createStaff(AdminCreateUserRequest request) {
        if (request.getRole() != Roles.AUTHOR && request.getRole() != Roles.EDITOR) {
            throw new CustomException(HttpStatus.BAD_REQUEST, MessageConstant.INVALID_PARAMETER.getDefaultMessage());
        }
        String email = request.getEmail().trim().toLowerCase();
        if (appUserRepository.existsByEmail(email)) {
            throw new CustomException(HttpStatus.CONFLICT, MessageConstant.EMAIL_ALREADY_EXISTS.getDefaultMessage());
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
        return mapper.map(user, AppUserDto.class);
    }

    @Override
    @Transactional
    public AppUserDto setActive(Long id, boolean active) {
        if (!active && id.equals(currentUserService.getCurrentUser().getId())) {
            throw new CustomException(HttpStatus.BAD_REQUEST, MessageConstant.CANNOT_DEACTIVATE_SELF.getDefaultMessage());
        }
        AppUser user = findUser(id);
        user.setActive(active);
        user = appUserRepository.save(user);
        if (!active) {
            revokeRefreshTokens(user);
        }
        return mapper.map(user, AppUserDto.class);
    }

    @Override
    @Transactional
    public AppUserDto changeRole(Long id, ChangeRoleRequest request) {
        if (id.equals(currentUserService.getCurrentUser().getId()) && request.getRole() != Roles.ADMIN) {
            throw new CustomException(HttpStatus.BAD_REQUEST, MessageConstant.CANNOT_REMOVE_OWN_ADMIN_ROLE.getDefaultMessage());
        }
        AppUser user = findUser(id);
        user.setRole(request.getRole());
        user = appUserRepository.save(user);

        syncUserRole(user);
        return mapper.map(user, AppUserDto.class);
    }

    @Override
    @Transactional
    public void resetPassword(Long id, AdminResetPasswordRequest request) {
        AppUser user = findUser(id);
        user.setPassword(passwordEncoder.encode(this.confirm(request.getPassword(), request.getConfirm())));
        appUserRepository.save(user);
        revokeRefreshTokens(user);
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
