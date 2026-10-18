package com.api.v1.bearerauth.security;

import com.api.v1.bearerauth.common.exception.CustomException;
import com.api.v1.bearerauth.model.entity.AppUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CurrentUserService {

    // for public endpoints: the logged-in user, or empty for a visitor
    public Optional<AppUser> findCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public AppUser getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !(auth.getPrincipal() instanceof AppUser user)) {
            throw new CustomException(HttpStatus.UNAUTHORIZED,"No authenticated user in context");
        }
        return user;
    }
}
