package com.api.v1.newssystem.repository;

import com.api.v1.newssystem.model.entity.AppUser;
import com.api.v1.newssystem.model.entity.AppUserToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AppUserTokenRepository extends JpaRepository<AppUserToken, Long> {
    Optional<List<AppUserToken>> findAllByUser(AppUser user);
}
