package com.api.v1.bearerauth.service.impl;

import com.api.v1.bearerauth.model.entity.AppUser;
import com.api.v1.bearerauth.model.entity.AppUserToken;
import com.api.v1.bearerauth.repository.AppUserTokenRepository;
import com.api.v1.bearerauth.service.AppUserTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AppUserTokenServiceImpl implements AppUserTokenService {
    private final AppUserTokenRepository appUserTokenRepository;

    @Override
    public void save(AppUserToken token) {
        appUserTokenRepository.save(token);
    }

    @Override
    public Optional<List<AppUserToken>> findAllByUser(AppUser user) {
        return appUserTokenRepository.findAllByUser(user);
    }

    @Override
    public void deleteAll(List<AppUserToken> tokens) {
        appUserTokenRepository.deleteAll(tokens);
    }
}
