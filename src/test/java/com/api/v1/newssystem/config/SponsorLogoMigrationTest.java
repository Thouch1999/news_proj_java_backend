package com.api.v1.newssystem.config;

import com.api.v1.newssystem.service.FileStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** An uploaded mobile bar image becomes the logo (256 and 128 px variants) and its creative row goes. */
@ExtendWith(MockitoExtension.class)
class SponsorLogoMigrationTest {
    @Mock
    private JdbcTemplate jdbc;
    @Mock
    private FileStorageService fileStorageService;

    private void rows(Object... values) {
        when(jdbc.queryForList(anyString())).thenReturn(List.<Map<String, Object>>of(Map.of("creative_id", 3L, "sponsor_id", 9L, "image_url", "/uploads/bar.png")));
    }

    @Test
    void anUploadedBarImage_becomesTheLogo_andTheCreativeAndTheOriginalFileGo() {
        rows();
        when(fileStorageService.storeSquareCrop("/uploads/bar.png", 256)).thenReturn("/uploads/l256.png");
        when(fileStorageService.storeSquareCrop("/uploads/bar.png", 128)).thenReturn("/uploads/l128.png");

        new SponsorLogoMigration(jdbc, fileStorageService).run(new DefaultApplicationArguments());

        verify(jdbc).update("update sponsors set logo_url = ?, logo_small_url = ? where id = ?", "/uploads/l256.png", "/uploads/l128.png", 9L);
        verify(jdbc).update("delete from sponsor_creatives where id = ?", 3L);
        verify(fileStorageService).delete("/uploads/bar.png");
    }

    @Test
    void aPictureThatCannotBeCut_isUsedAsItIs_andItsFileIsKept() {
        rows();
        when(fileStorageService.storeSquareCrop(anyString(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(null);

        new SponsorLogoMigration(jdbc, fileStorageService).run(new DefaultApplicationArguments());

        verify(jdbc).update("update sponsors set logo_url = ?, logo_small_url = ? where id = ?", "/uploads/bar.png", "/uploads/bar.png", 9L);
        verify(fileStorageService, never()).delete("/uploads/bar.png");
    }

    @Test
    void withNothingToMove_nothingChanges_andAFailureNeverStopsTheStart() {
        when(jdbc.queryForList(anyString())).thenReturn(List.of());
        new SponsorLogoMigration(jdbc, fileStorageService).run(new DefaultApplicationArguments());
        verify(jdbc, never()).update(anyString(), org.mockito.ArgumentMatchers.<Object[]>any());

        when(jdbc.queryForList(anyString())).thenThrow(new RuntimeException("no table"));
        new SponsorLogoMigration(jdbc, fileStorageService).run(new DefaultApplicationArguments());
    }
}
