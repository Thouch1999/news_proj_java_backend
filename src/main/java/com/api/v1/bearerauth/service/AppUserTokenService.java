package com.api.v1.bearerauth.service;

import com.api.v1.bearerauth.model.entity.AppUser;
import com.api.v1.bearerauth.model.entity.AppUserToken;

import java.util.List;
import java.util.Optional;

public interface AppUserTokenService {
    void save(AppUserToken token);
    Optional<List<AppUserToken>> findAllByUser(AppUser user);
    void deleteAll(List<AppUserToken> tokens);
}
