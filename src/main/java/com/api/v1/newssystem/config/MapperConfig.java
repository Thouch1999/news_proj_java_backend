package com.api.v1.newssystem.config;

import com.api.v1.newssystem.model.entity.AppUser;
import com.api.v1.newssystem.model.response.AppUserDto;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MapperConfig {
    @Bean
    public ModelMapper modelMapper() {
        ModelMapper mapper = new ModelMapper();
        mapper.getConfiguration().setMatchingStrategy(MatchingStrategies.STRICT);
        mapper.typeMap(AppUser.class, AppUserDto.class).addMappings(mapping ->
                mapping.map(AppUser::getName, AppUserDto::setUsername)
        );
        return mapper;
    }
}
