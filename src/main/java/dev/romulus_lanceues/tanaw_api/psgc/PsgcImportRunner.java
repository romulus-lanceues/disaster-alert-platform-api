package dev.romulus_lanceues.tanaw_api.psgc;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.file.Path;


@Component
@RequiredArgsConstructor
@Profile("psgc-import")
public class PsgcImportRunner implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(PsgcImportRunner.class);

    private final PsgcImportService psgcImportService;

    @Value("${psgc.file}")
    String file;

    @Override
    public void run(String... args) throws Exception {
        logger.info("Importing Psgc File from: {} ", file);
        var summary =  psgcImportService.importFile(Path.of(file));
        logger.info("PSGC import finished: {} areas in file, {} deactivated",
                summary.rowsInFile(), summary.deactivated());
    }
}
