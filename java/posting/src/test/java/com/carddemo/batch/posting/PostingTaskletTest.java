package com.carddemo.batch.posting;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Arrays;

import com.carddemo.batch.common.codec.CardDemoLayout;
import com.carddemo.batch.common.codec.FixedRecordCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostingTaskletTest {
    @TempDir
    Path directory;

    @Test
    void missingCategoriesInheritFillerFromLastSuccessfulRead() throws Exception {
        var layout = CardDemoLayout.CVTRA06Y.load();
        var codec = new FixedRecordCodec(layout);
        byte[] blank = new byte[layout.length()];
        Arrays.fill(blank, (byte) ' ');
        var daily = codec.decode(blank);
        var amount = new BigDecimal("-2.55");
        daily.put("DALYTRAN-AMT", amount);
        daily.put("DALYTRAN-CARD-NUM", "0000000000000001");
        daily.put("DALYTRAN-ORIG-TS", "2022-07-04-09.15.00.000000");
        var file = new StringBuilder();
        for (int i = 1; i <= 3; i++) {
            daily.put("DALYTRAN-ID", "TRAN" + i);
            daily.put("DALYTRAN-TYPE-CD", "0" + i);
            file.append(new String(codec.encode(daily), ISO_8859_1)).append('\n');
        }
        Files.writeString(directory.resolve(PostingTasklet.PREFIX + "DALYTRAN.PS.txt"), file, ISO_8859_1);

        var store = mock(PostingStore.class);
        when(store.findAccountId("0000000000000001")).thenReturn(1L);
        when(store.findAccount(1L)).thenReturn(new PostingStore.Account(
                BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, "2023-01-01"));
        when(store.updateCategoryBalance(1L, "01", 0, amount)).thenReturn("PRESERVED FILLER");

        var params = new JobParametersBuilder()
                .addString("input", directory.toString())
                .addString("out", directory.resolve("out").toString())
                .toJobParameters();
        var step = new JobExecution(1L, params).createStepExecution("STEP15");
        new PostingTasklet(store, Clock.systemUTC())
                .execute(step.createStepContribution(), new ChunkContext(new StepContext(step)));

        verify(store).createCategoryBalance(1L, "02", 0, amount, "PRESERVED FILLER");
        verify(store).createCategoryBalance(1L, "03", 0, amount, "PRESERVED FILLER");
    }
}
