package com.api.v1.newssystem.service.impl;

import com.api.v1.newssystem.common.constant.MessageConstant;
import com.api.v1.newssystem.common.exception.CustomException;
import com.api.v1.newssystem.config.MapperConfig;
import com.api.v1.newssystem.model.entity.AppUser;
import com.api.v1.newssystem.model.entity.Role;
import com.api.v1.newssystem.model.entity.UserRole;
import com.api.v1.newssystem.model.enumerate.Roles;
import com.api.v1.newssystem.model.request.AdminCreateUserRequest;
import com.api.v1.newssystem.model.request.AdminResetPasswordRequest;
import com.api.v1.newssystem.model.request.ChangeRoleRequest;
import com.api.v1.newssystem.model.response.AppUserDto;
import com.api.v1.newssystem.repository.AppUserRepository;
import com.api.v1.newssystem.repository.RoleRepository;
import com.api.v1.newssystem.repository.UserRoleRepository;
import com.api.v1.newssystem.security.CurrentUserService;
import com.api.v1.newssystem.service.AppUserTokenService;
import com.api.v1.newssystem.service.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppUserServiceImplTest {
    private static final long ADMIN_ID = 1L;
    private static final long OTHER_ID = 2L;

    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private AppUserTokenService tokenService;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private UserRoleRepository userRoleRepository;
    @Mock
    private FileStorageService fileStorageService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private AppUserServiceImpl service;
    private AppUser admin;

    @Mock
    private com.api.v1.newssystem.service.ActivityLogService activityLogService;

    @BeforeEach
    void setUp() {
        service = new AppUserServiceImpl(appUserRepository, tokenService, currentUserService, passwordEncoder,
                new MapperConfig().modelMapper(), roleRepository, userRoleRepository, fileStorageService, eventPublisher, activityLogService);
        admin = user(ADMIN_ID, Roles.ADMIN);
    }

    private AppUser user(long id, Roles role) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setUsername("User " + id);
        user.setEmail("user" + id + "@news.local");
        user.setPassword(passwordEncoder.encode("Password-123"));
        user.setRole(role);
        user.setActive(true);
        user.setEmailVerified(true);
        return user;
    }

    private void loggedInAsAdmin() {
        when(currentUserService.getCurrentUser()).thenReturn(admin);
    }

    private void assertFails(Runnable call, MessageConstant expected, HttpStatus status) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(CustomException.class, e -> {
                    assertThat(e.getMessage()).isEqualTo(expected.getDefaultMessage());
                    assertThat(e.getHttpStatus()).isEqualTo(status);
                });
    }

    private AdminCreateUserRequest createRequest(Roles role) {
        AdminCreateUserRequest request = new AdminCreateUserRequest();
        request.setUsername("New Editor");
        request.setEmail(" New.Editor@News.local ");
        request.setPassword("Password-123");
        request.setConfirm("Password-123");
        request.setRole(role);
        return request;
    }

    @Test
    void createStaff_createsActiveVerifiedAccount_withHashedPassword_andRoleLink() {
        Role editorRole = new Role();
        editorRole.setId(10L);
        editorRole.setName("EDITOR");
        when(appUserRepository.existsByEmail("new.editor@news.local")).thenReturn(false);
        when(appUserRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(roleRepository.findByName("EDITOR")).thenReturn(Optional.of(editorRole));
        when(userRoleRepository.findAllByUserId(any())).thenReturn(List.of());

        AppUserDto created = service.createStaff(createRequest(Roles.EDITOR));

        ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("new.editor@news.local");
        assertThat(saved.getValue().isEmailVerified()).isTrue();
        assertThat(saved.getValue().isActive()).isTrue();
        assertThat(saved.getValue().getRole()).isEqualTo(Roles.EDITOR);
        assertThat(saved.getValue().getPassword()).isNotEqualTo("Password-123");
        assertThat(passwordEncoder.matches("Password-123", saved.getValue().getPassword())).isTrue();
        assertThat(created.getRole()).isEqualTo(Roles.EDITOR);

        ArgumentCaptor<UserRole> link = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleRepository).save(link.capture());
        assertThat(link.getValue().getRole()).isSameAs(editorRole);
    }

    // ---------------- avatar ----------------

    private org.springframework.mock.web.MockMultipartFile picture() {
        return new org.springframework.mock.web.MockMultipartFile("file", "me.png", "image/png", new byte[]{1, 2, 3});
    }

    private void adminCanBeFoundAndSaved() {
        loggedInAsAdmin();
        when(appUserRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        when(appUserRepository.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void updateAvatar_savesTheNewPath_andDeletesTheFileItReplaces() {
        adminCanBeFoundAndSaved();
        admin.setAvatar("/uploads/old.png");
        var file = picture();
        when(fileStorageService.store(file)).thenReturn("/uploads/new.png");

        AppUserDto updated = service.updateAvatar(file);

        assertThat(updated.getAvatar()).isEqualTo("/uploads/new.png");
        verify(fileStorageService).delete("/uploads/old.png");
    }

    @Test
    void updateAvatar_ofARejectedFile_changesNothing() {
        loggedInAsAdmin();
        when(appUserRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        admin.setAvatar("/uploads/old.png");
        var file = picture();
        when(fileStorageService.store(file))
                .thenThrow(new CustomException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, MessageConstant.FILE_TYPE_NOT_ALLOWED.getDefaultMessage()));

        assertFails(() -> service.updateAvatar(file), MessageConstant.FILE_TYPE_NOT_ALLOWED, HttpStatus.UNSUPPORTED_MEDIA_TYPE);

        assertThat(admin.getAvatar()).isEqualTo("/uploads/old.png");
        verify(appUserRepository, org.mockito.Mockito.never()).save(any());
        verify(fileStorageService, org.mockito.Mockito.never()).delete(any());
    }

    @Test
    void removeAvatar_clearsIt_andDeletesTheFile() {
        adminCanBeFoundAndSaved();
        admin.setAvatar("/uploads/old.png");

        AppUserDto updated = service.removeAvatar();

        assertThat(updated.getAvatar()).isNull();
        verify(fileStorageService).delete("/uploads/old.png");
    }

    @Test
    void update_withAnotherAvatar_deletesTheReplacedFile_butAnUnchangedOneIsKept() {
        adminCanBeFoundAndSaved();
        admin.setAvatar("/uploads/old.png");
        var request = new com.api.v1.newssystem.model.request.UpdateProfileRequest();
        request.setUsername("Admin");
        request.setAvatar("/uploads/old.png");

        service.update(request);
        verify(fileStorageService, org.mockito.Mockito.never()).delete(any());

        request.setAvatar("https://example.com/me.png");
        service.update(request);
        verify(fileStorageService).delete("/uploads/old.png");
    }

    @Test
    void createStaff_theSuperAdminCreatesEachOfTheFourRoles_activeAndVerified_andTheLogHasTheRole() {
        admin.setSuperAdmin(true);
        loggedInAsAdmin();
        when(appUserRepository.existsByEmail("new.editor@news.local")).thenReturn(false);
        when(appUserRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(userRoleRepository.findAllByUserId(any())).thenReturn(List.of());
        long roleId = 20;
        for (Roles role : Roles.values()) {
            Role row = new Role();
            row.setId(roleId++);
            row.setName(role.name());
            when(roleRepository.findByName(role.name())).thenReturn(Optional.of(row));
        }

        for (Roles role : Roles.values()) {
            org.mockito.Mockito.clearInvocations(appUserRepository, activityLogService);

            AppUserDto created = service.createStaff(createRequest(role));

            ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
            verify(appUserRepository).save(saved.capture());
            assertThat(saved.getValue().getRole()).as(role.name()).isEqualTo(role);
            assertThat(saved.getValue().isActive()).as(role.name()).isTrue();
            assertThat(saved.getValue().isEmailVerified()).as(role.name()).isTrue();
            assertThat(created.getRole()).isEqualTo(role);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<java.util.Map<String, Object>> details = ArgumentCaptor.forClass(java.util.Map.class);
            verify(activityLogService).record(org.mockito.ArgumentMatchers.eq(com.api.v1.newssystem.model.enumerate.ActivityAction.USER_CREATED),
                    org.mockito.ArgumentMatchers.eq(com.api.v1.newssystem.model.enumerate.ActivityTargetType.USER), any(),
                    org.mockito.ArgumentMatchers.eq("New Editor"), details.capture());
            assertThat(details.getValue()).containsEntry("role", role); // creating an ADMIN is visible in the log
            assertThat(details.getValue().get("adminRoleGranted")).isEqualTo(role == Roles.ADMIN ? true : null);
            assertThat(details.getValue().toString()).doesNotContain("Password-123");
        }
    }

    @Test
    void createStaff_aRoleThatIsNotInTheRolesTable_isRejected_andNothingIsSaved() {
        when(appUserRepository.existsByEmail("new.editor@news.local")).thenReturn(false);
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.empty());

        assertFails(() -> service.createStaff(createRequest(Roles.ADMIN)), MessageConstant.INVALID_PARAMETER, HttpStatus.BAD_REQUEST);
        assertFails(() -> service.createStaff(createRequest(null)), MessageConstant.INVALID_PARAMETER, HttpStatus.BAD_REQUEST);

        verify(appUserRepository, never()).save(any());
        verify(activityLogService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void createStaff_duplicateEmailIsRejected() {
        when(appUserRepository.existsByEmail("new.editor@news.local")).thenReturn(true);

        assertFails(() -> service.createStaff(createRequest(Roles.AUTHOR)), MessageConstant.EMAIL_ALREADY_EXISTS, HttpStatus.CONFLICT);

        verify(appUserRepository, never()).save(any());
    }

    @Test
    void deactivate_self_isRejected() {
        loggedInAsAdmin();

        assertFails(() -> service.setActive(ADMIN_ID, false), MessageConstant.CANNOT_DEACTIVATE_SELF, HttpStatus.BAD_REQUEST);

        assertThat(admin.isActive()).isTrue();
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void deactivate_otherUser_deactivatesAndRevokesTokens() {
        loggedInAsAdmin();
        AppUser other = user(OTHER_ID, Roles.AUTHOR);
        when(appUserRepository.findById(OTHER_ID)).thenReturn(Optional.of(other));
        when(appUserRepository.save(other)).thenReturn(other);

        AppUserDto result = service.setActive(OTHER_ID, false);

        assertThat(other.isActive()).isFalse();
        assertThat(result.isActive()).isFalse();
        verify(tokenService).findAllByUser(other);
    }

    @Test
    void activate_otherUser_works() {
        AppUser other = user(OTHER_ID, Roles.AUTHOR);
        other.setActive(false);
        when(appUserRepository.findById(OTHER_ID)).thenReturn(Optional.of(other));
        when(appUserRepository.save(other)).thenReturn(other);

        service.setActive(OTHER_ID, true);

        assertThat(other.isActive()).isTrue();
        verify(tokenService, never()).findAllByUser(any());
    }

    @Test
    void changeRole_ownAdminRole_cannotBeRemoved() {
        loggedInAsAdmin();
        ChangeRoleRequest request = new ChangeRoleRequest();
        request.setRole(Roles.EDITOR);

        assertFails(() -> service.changeRole(ADMIN_ID, request), MessageConstant.CANNOT_REMOVE_OWN_ADMIN_ROLE, HttpStatus.BAD_REQUEST);

        assertThat(admin.getRole()).isEqualTo(Roles.ADMIN);
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void changeRole_otherUser_updatesRole_andReplacesTheRoleLink() {
        loggedInAsAdmin();
        AppUser other = user(OTHER_ID, Roles.READER);
        Role oldRole = new Role();
        oldRole.setId(11L);
        Role newRole = new Role();
        newRole.setId(12L);
        UserRole oldLink = new UserRole();
        oldLink.setRole(oldRole);
        when(appUserRepository.findById(OTHER_ID)).thenReturn(Optional.of(other));
        when(appUserRepository.save(other)).thenReturn(other);
        when(roleRepository.findByName("AUTHOR")).thenReturn(Optional.of(newRole));
        when(userRoleRepository.findAllByUserId(OTHER_ID)).thenReturn(List.of(oldLink));
        ChangeRoleRequest request = new ChangeRoleRequest();
        request.setRole(Roles.AUTHOR);

        AppUserDto result = service.changeRole(OTHER_ID, request);

        assertThat(result.getRole()).isEqualTo(Roles.AUTHOR);
        verify(userRoleRepository).delete(oldLink);
        ArgumentCaptor<UserRole> added = ArgumentCaptor.forClass(UserRole.class);
        verify(userRoleRepository).save(added.capture());
        assertThat(added.getValue().getRole()).isSameAs(newRole);
    }

    @Test
    void changeRole_otherUser_notifiesThatUserOnly_withTheNewAndThePreviousRole() {
        loggedInAsAdmin();
        AppUser other = user(OTHER_ID, Roles.READER);
        Role newRole = new Role();
        newRole.setId(12L);
        when(appUserRepository.findById(OTHER_ID)).thenReturn(Optional.of(other));
        when(appUserRepository.save(other)).thenReturn(other);
        when(roleRepository.findByName("AUTHOR")).thenReturn(Optional.of(newRole));
        when(userRoleRepository.findAllByUserId(OTHER_ID)).thenReturn(List.of());
        ChangeRoleRequest request = new ChangeRoleRequest();
        request.setRole(Roles.AUTHOR);

        service.changeRole(OTHER_ID, request);

        org.mockito.ArgumentCaptor<com.api.v1.newssystem.service.NotificationEvent> event =
                org.mockito.ArgumentCaptor.forClass(com.api.v1.newssystem.service.NotificationEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().type()).isEqualTo(com.api.v1.newssystem.model.enumerate.NotificationType.ROLES_CHANGED);
        assertThat(event.getValue().recipientId()).isEqualTo(OTHER_ID);
        assertThat(event.getValue().actorId()).isEqualTo(ADMIN_ID);
        assertThat(event.getValue().data()).containsEntry("roles", List.of("AUTHOR")).containsEntry("previousRoles", List.of("READER"));
    }

    @Test
    void changeRole_toTheSameRole_doesNotNotify() {
        loggedInAsAdmin();
        Role adminRole = new Role();
        adminRole.setId(13L);
        when(appUserRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        when(appUserRepository.save(admin)).thenReturn(admin);
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(adminRole));
        when(userRoleRepository.findAllByUserId(ADMIN_ID)).thenReturn(List.of());
        ChangeRoleRequest request = new ChangeRoleRequest();
        request.setRole(Roles.ADMIN);

        service.changeRole(ADMIN_ID, request);

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void changeRole_ownAdminRole_toAdminAgain_isAllowed() {
        loggedInAsAdmin();
        Role adminRole = new Role();
        adminRole.setId(13L);
        when(appUserRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        when(appUserRepository.save(admin)).thenReturn(admin);
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(adminRole));
        when(userRoleRepository.findAllByUserId(ADMIN_ID)).thenReturn(List.of());
        ChangeRoleRequest request = new ChangeRoleRequest();
        request.setRole(Roles.ADMIN);

        assertThat(service.changeRole(ADMIN_ID, request).getRole()).isEqualTo(Roles.ADMIN);
    }

    @Test
    void resetPassword_hashesNewPassword_andRevokesTokens() {
        AppUser other = user(OTHER_ID, Roles.AUTHOR);
        when(appUserRepository.findById(OTHER_ID)).thenReturn(Optional.of(other));
        AdminResetPasswordRequest request = new AdminResetPasswordRequest();
        request.setPassword("Brand-New-9");
        request.setConfirm("Brand-New-9");

        service.resetPassword(OTHER_ID, request);

        assertThat(passwordEncoder.matches("Brand-New-9", other.getPassword())).isTrue();
        verify(appUserRepository).save(other);
        verify(tokenService).findAllByUser(other);
    }

    @Test
    void resetPassword_confirmMismatch_isRejected() {
        AppUser other = user(OTHER_ID, Roles.AUTHOR);
        when(appUserRepository.findById(OTHER_ID)).thenReturn(Optional.of(other));
        AdminResetPasswordRequest request = new AdminResetPasswordRequest();
        request.setPassword("Brand-New-9");
        request.setConfirm("Different-9");

        assertFails(() -> service.resetPassword(OTHER_ID, request), MessageConstant.CONFIRM_PASSWORD_NOT_MATCH, HttpStatus.BAD_REQUEST);

        verify(appUserRepository, never()).save(any());
    }

    @Test
    void findAll_normalisesPagingAndBlankSearch_andPassesFilters() {
        when(appUserRepository.search(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(user(OTHER_ID, Roles.AUTHOR))));

        Page<AppUserDto> result = service.findAll("   ", Roles.AUTHOR, true, false, 0, 1000);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(appUserRepository).search(eq(null), eq(Roles.AUTHOR), eq(true), eq(false), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getRole()).isEqualTo(Roles.AUTHOR);
    }

    @Test
    void findById_unknownUser_isNotFound() {
        when(appUserRepository.findById(99L)).thenReturn(Optional.empty());

        assertFails(() -> service.findById(99L), MessageConstant.USER_NOT_FOUND, HttpStatus.NOT_FOUND);
    }

    // ---------------- super admin ----------------

    private AppUser superAdmin;

    private AppUser superAdminLoggedIn() {
        superAdmin = user(50L, Roles.ADMIN);
        superAdmin.setSuperAdmin(true);
        when(currentUserService.getCurrentUser()).thenReturn(superAdmin);
        return superAdmin;
    }

    private ChangeRoleRequest roleRequest(Roles role) {
        ChangeRoleRequest request = new ChangeRoleRequest();
        request.setRole(role);
        return request;
    }

    private AdminResetPasswordRequest passwordRequest() {
        AdminResetPasswordRequest request = new AdminResetPasswordRequest();
        request.setPassword("Brand-New-9");
        request.setConfirm("Brand-New-9");
        return request;
    }

    @Test
    void superAdmin_cannotBeDeactivated_byAnAdmin_orByThemself() {
        AppUser boss = superAdminLoggedIn();
        assertFails(() -> service.setActive(50L, false), MessageConstant.SUPER_ADMIN_CANNOT_BE_DEACTIVATED, HttpStatus.FORBIDDEN);

        loggedInAsAdmin();
        when(appUserRepository.findById(50L)).thenReturn(Optional.of(boss));
        assertFails(() -> service.setActive(50L, false), MessageConstant.SUPER_ADMIN_CANNOT_BE_DEACTIVATED, HttpStatus.FORBIDDEN);

        assertThat(boss.isActive()).isTrue();
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void superAdmin_roleCannotBeChanged_byAnybody_notEvenByThemself() {
        AppUser boss = superAdminLoggedIn();
        assertFails(() -> service.changeRole(50L, roleRequest(Roles.EDITOR)), MessageConstant.SUPER_ADMIN_ROLE_PROTECTED, HttpStatus.FORBIDDEN);

        loggedInAsAdmin();
        when(appUserRepository.findById(50L)).thenReturn(Optional.of(boss));
        assertFails(() -> service.changeRole(50L, roleRequest(Roles.EDITOR)), MessageConstant.SUPER_ADMIN_ROLE_PROTECTED, HttpStatus.FORBIDDEN);

        assertThat(boss.getRole()).isEqualTo(Roles.ADMIN);
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void superAdmin_passwordCannotBeResetBySomeoneElse() {
        AppUser boss = superAdminLoggedIn();
        when(appUserRepository.findById(50L)).thenReturn(Optional.of(boss));
        String before = boss.getPassword();

        assertFails(() -> service.resetPassword(50L, passwordRequest()), MessageConstant.SUPER_ADMIN_PASSWORD_PROTECTED, HttpStatus.FORBIDDEN);
        loggedInAsAdmin();
        assertFails(() -> service.resetPassword(50L, passwordRequest()), MessageConstant.SUPER_ADMIN_PASSWORD_PROTECTED, HttpStatus.FORBIDDEN);

        assertThat(boss.getPassword()).isEqualTo(before);
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void normalAdmin_cannotCreateAnAdmin() {
        loggedInAsAdmin();
        Role adminRole = new Role();
        adminRole.setName("ADMIN");
        when(appUserRepository.existsByEmail("new.editor@news.local")).thenReturn(false);
        when(roleRepository.findByName("ADMIN")).thenReturn(Optional.of(adminRole));

        assertFails(() -> service.createStaff(createRequest(Roles.ADMIN)), MessageConstant.SUPER_ADMIN_ONLY, HttpStatus.FORBIDDEN);

        verify(appUserRepository, never()).save(any());
    }

    @Test
    void normalAdmin_cannotGiveOrRemoveTheAdminRole() {
        loggedInAsAdmin();
        AppUser author = user(OTHER_ID, Roles.AUTHOR);
        AppUser otherAdmin = user(3L, Roles.ADMIN);
        when(appUserRepository.findById(OTHER_ID)).thenReturn(Optional.of(author));
        when(appUserRepository.findById(3L)).thenReturn(Optional.of(otherAdmin));

        assertFails(() -> service.changeRole(OTHER_ID, roleRequest(Roles.ADMIN)), MessageConstant.SUPER_ADMIN_ONLY, HttpStatus.FORBIDDEN);
        assertFails(() -> service.changeRole(3L, roleRequest(Roles.EDITOR)), MessageConstant.SUPER_ADMIN_ONLY, HttpStatus.FORBIDDEN);

        assertThat(author.getRole()).isEqualTo(Roles.AUTHOR);
        assertThat(otherAdmin.getRole()).isEqualTo(Roles.ADMIN);
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void normalAdmin_cannotDeactivateOrResetThePasswordOfAnotherAdmin() {
        loggedInAsAdmin();
        AppUser otherAdmin = user(3L, Roles.ADMIN);
        when(appUserRepository.findById(3L)).thenReturn(Optional.of(otherAdmin));

        assertFails(() -> service.setActive(3L, false), MessageConstant.SUPER_ADMIN_ONLY, HttpStatus.FORBIDDEN);
        assertFails(() -> service.resetPassword(3L, passwordRequest()), MessageConstant.SUPER_ADMIN_ONLY, HttpStatus.FORBIDDEN);

        assertThat(otherAdmin.isActive()).isTrue();
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void normalAdmin_keepsTheOtherAbilities_onNonAdmins_andOnTheirOwnPassword() {
        loggedInAsAdmin();
        AppUser author = user(OTHER_ID, Roles.AUTHOR);
        when(appUserRepository.findById(OTHER_ID)).thenReturn(Optional.of(author));
        when(appUserRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
        when(appUserRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        Role editorRole = new Role();
        editorRole.setId(12L);
        when(roleRepository.findByName("EDITOR")).thenReturn(Optional.of(editorRole));
        when(userRoleRepository.findAllByUserId(any())).thenReturn(List.of());

        service.setActive(OTHER_ID, false);
        service.resetPassword(OTHER_ID, passwordRequest());
        service.changeRole(OTHER_ID, roleRequest(Roles.EDITOR));
        service.resetPassword(ADMIN_ID, passwordRequest());

        assertThat(author.isActive()).isFalse();
        assertThat(author.getRole()).isEqualTo(Roles.EDITOR);
    }

    @Test
    void superAdmin_canCreateAdmin_giveAndRemoveTheAdminRole_deactivateAndResetAnotherAdmin() {
        AppUser boss = superAdminLoggedIn();
        AppUser author = user(OTHER_ID, Roles.AUTHOR);
        AppUser otherAdmin = user(3L, Roles.ADMIN);
        when(appUserRepository.findById(OTHER_ID)).thenReturn(Optional.of(author));
        when(appUserRepository.findById(3L)).thenReturn(Optional.of(otherAdmin));
        when(appUserRepository.countByRoleAndActiveTrue(Roles.ADMIN)).thenReturn(2L);
        when(appUserRepository.existsByEmail("new.editor@news.local")).thenReturn(false);
        when(appUserRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        Role anyRole = new Role();
        anyRole.setId(12L);
        when(roleRepository.findByName(any())).thenReturn(Optional.of(anyRole));
        when(userRoleRepository.findAllByUserId(any())).thenReturn(List.of());

        assertThat(service.createStaff(createRequest(Roles.ADMIN)).getRole()).isEqualTo(Roles.ADMIN);
        service.changeRole(OTHER_ID, roleRequest(Roles.ADMIN));
        assertThat(author.getRole()).isEqualTo(Roles.ADMIN);
        service.resetPassword(3L, passwordRequest());
        service.setActive(3L, false);
        assertThat(otherAdmin.isActive()).isFalse();
        otherAdmin.setActive(true);
        service.changeRole(3L, roleRequest(Roles.EDITOR));
        assertThat(otherAdmin.getRole()).isEqualTo(Roles.EDITOR);
        assertThat(boss.isSuperAdmin()).isTrue();
    }

    @Test
    void theLastActiveAdmin_cannotBeDeactivatedOrDemoted_evenBySuperAdmin() {
        superAdminLoggedIn();
        AppUser otherAdmin = user(3L, Roles.ADMIN);
        when(appUserRepository.findById(3L)).thenReturn(Optional.of(otherAdmin));
        when(appUserRepository.countByRoleAndActiveTrue(Roles.ADMIN)).thenReturn(1L);

        assertFails(() -> service.setActive(3L, false), MessageConstant.LAST_ADMIN_REQUIRED, HttpStatus.CONFLICT);
        assertFails(() -> service.changeRole(3L, roleRequest(Roles.EDITOR)), MessageConstant.LAST_ADMIN_REQUIRED, HttpStatus.CONFLICT);

        verify(appUserRepository, never()).save(any());
    }

    @Test
    void roleChange_toAndFromAdmin_isFlaggedInTheActivityLog() {
        superAdminLoggedIn();
        AppUser author = user(OTHER_ID, Roles.AUTHOR);
        when(appUserRepository.findById(OTHER_ID)).thenReturn(Optional.of(author));
        when(appUserRepository.countByRoleAndActiveTrue(Roles.ADMIN)).thenReturn(2L);
        when(appUserRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        when(roleRepository.findByName(any())).thenReturn(Optional.of(new Role()));
        when(userRoleRepository.findAllByUserId(any())).thenReturn(List.of());

        service.changeRole(OTHER_ID, roleRequest(Roles.ADMIN));
        service.changeRole(OTHER_ID, roleRequest(Roles.READER));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Map<String, Object>> details = ArgumentCaptor.forClass(java.util.Map.class);
        verify(activityLogService, org.mockito.Mockito.times(2)).record(eq(com.api.v1.newssystem.model.enumerate.ActivityAction.USER_ROLES_CHANGED),
                any(), any(), any(), details.capture());
        assertThat(details.getAllValues().get(0)).containsEntry("adminRoleGranted", true).doesNotContainKey("adminRoleRemoved");
        assertThat(details.getAllValues().get(1)).containsEntry("adminRoleRemoved", true).doesNotContainKey("adminRoleGranted");
    }

    // ---------------- transfer ----------------

    private AppUser transferTarget() {
        AppUser target = user(3L, Roles.ADMIN);
        when(appUserRepository.findById(3L)).thenReturn(Optional.of(target));
        return target;
    }

    private com.api.v1.newssystem.model.request.TransferSuperAdminRequest transfer(long userId, String password) {
        var request = new com.api.v1.newssystem.model.request.TransferSuperAdminRequest();
        request.setUserId(userId);
        request.setPassword(password);
        return request;
    }

    @Test
    void transfer_withTheRightPassword_movesTheMark_andTheOldOneBecomesANormalAdmin() {
        AppUser boss = superAdminLoggedIn();
        when(appUserRepository.findById(50L)).thenReturn(Optional.of(boss));
        AppUser target = transferTarget();
        when(appUserRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        AppUserDto result = service.transferSuperAdmin(transfer(3L, "Password-123"));

        assertThat(result.getId()).isEqualTo(3L);
        assertThat(result.isSuperAdmin()).isTrue();
        assertThat(target.isSuperAdmin()).isTrue();
        assertThat(boss.isSuperAdmin()).isFalse();
        assertThat(boss.getRole()).isEqualTo(Roles.ADMIN);
        // the old mark is written before the new one
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(appUserRepository);
        order.verify(appUserRepository).saveAndFlush(boss);
        order.verify(appUserRepository).saveAndFlush(target);
        verify(activityLogService).record(eq(com.api.v1.newssystem.model.enumerate.ActivityAction.SUPER_ADMIN_TRANSFERRED),
                eq(com.api.v1.newssystem.model.enumerate.ActivityTargetType.USER), eq(3L), any(), any());
    }

    @Test
    void transfer_withAWrongPassword_changesNothing() {
        AppUser boss = superAdminLoggedIn();
        when(appUserRepository.findById(50L)).thenReturn(Optional.of(boss));

        assertFails(() -> service.transferSuperAdmin(transfer(3L, "wrong-password")), MessageConstant.CURRENT_PASSWORD_INCORRECT, HttpStatus.BAD_REQUEST);

        assertThat(boss.isSuperAdmin()).isTrue();
        verify(appUserRepository, never()).saveAndFlush(any());
        verify(activityLogService, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    void transfer_toAnInvalidTarget_isRejected_andChangesNothing() {
        AppUser boss = superAdminLoggedIn();
        when(appUserRepository.findById(50L)).thenReturn(Optional.of(boss));
        AppUser target = transferTarget();

        for (Runnable invalid : List.<Runnable>of(
                () -> service.transferSuperAdmin(transfer(50L, "Password-123")),   // themself
                () -> { target.setRole(Roles.EDITOR); service.transferSuperAdmin(transfer(3L, "Password-123")); }, // not an admin
                () -> { target.setRole(Roles.ADMIN); target.setActive(false); service.transferSuperAdmin(transfer(3L, "Password-123")); }, // inactive
                () -> { target.setActive(true); target.setEmailVerified(false); service.transferSuperAdmin(transfer(3L, "Password-123")); } // not verified
        )) {
            assertFails(invalid, MessageConstant.SUPER_ADMIN_TRANSFER_INVALID_TARGET, HttpStatus.BAD_REQUEST);
        }
        when(appUserRepository.findById(404L)).thenReturn(Optional.empty());
        assertFails(() -> service.transferSuperAdmin(transfer(404L, "Password-123")), MessageConstant.USER_NOT_FOUND, HttpStatus.NOT_FOUND);

        assertThat(boss.isSuperAdmin()).isTrue();
        assertThat(target.isSuperAdmin()).isFalse();
        verify(appUserRepository, never()).saveAndFlush(any());
    }

    @Test
    void transfer_byANormalAdmin_isRejected() {
        loggedInAsAdmin();
        when(appUserRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));

        assertFails(() -> service.transferSuperAdmin(transfer(3L, "Password-123")), MessageConstant.SUPER_ADMIN_ONLY, HttpStatus.FORBIDDEN);

        verify(appUserRepository, never()).saveAndFlush(any());
    }
}
