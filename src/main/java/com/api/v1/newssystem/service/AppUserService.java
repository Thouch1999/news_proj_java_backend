package com.api.v1.newssystem.service;

import com.api.v1.newssystem.model.enumerate.Roles;
import com.api.v1.newssystem.model.request.AdminCreateUserRequest;
import com.api.v1.newssystem.model.request.AdminResetPasswordRequest;
import com.api.v1.newssystem.model.request.AppUserAddRequest;
import com.api.v1.newssystem.model.request.ChangeRoleRequest;
import com.api.v1.newssystem.model.request.TransferSuperAdminRequest;
import com.api.v1.newssystem.model.request.UpdateProfileRequest;
import com.api.v1.newssystem.model.response.AppUserDto;
import org.springframework.data.domain.Page;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.multipart.MultipartFile;

public interface AppUserService extends UserDetailsService {
    AppUserDto create(AppUserAddRequest request);
    /** Updates the profile of the logged-in user only. */
    AppUserDto update(UpdateProfileRequest request);
    /** Stores an uploaded image as the avatar of the logged-in user (the previous uploaded file is deleted). */
    AppUserDto updateAvatar(MultipartFile file);
    /** Clears the avatar of the logged-in user (an uploaded file is deleted). */
    AppUserDto removeAvatar();
    AppUserDto current();
    void logout();

    // ---- admin ----

    /** Lists users; every filter is optional and page is 1-based. */
    Page<AppUserDto> findAll(String search, Roles role, Boolean active, Boolean emailVerified, int page, int size);

    AppUserDto findById(Long id);

    /** Creates an account with any role (ADMIN, EDITOR, AUTHOR or READER) that is active and email-verified. */
    AppUserDto createStaff(AdminCreateUserRequest request);

    /** Activates or deactivates a user; an admin cannot deactivate themselves. */
    AppUserDto setActive(Long id, boolean active);

    /** Sets the user's single role; an admin cannot move themselves away from ADMIN. */
    AppUserDto changeRole(Long id, ChangeRoleRequest request);

    void resetPassword(Long id, AdminResetPasswordRequest request);

    /**
     * Super admin only: moves the super admin mark to another active, email-verified ADMIN after checking the
     * current password of the super admin. Returns the new super admin.
     */
    AppUserDto transferSuperAdmin(TransferSuperAdminRequest request);
}
