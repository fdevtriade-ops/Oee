package br.com.telemetria.api.configuracao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ConferenciaBanco implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(ConferenciaBanco.class);
    private final ConfiguracaoRepository repository;

    public ConferenciaBanco(ConfiguracaoRepository repository) { this.repository = repository; }

    @Override
    public void run(ApplicationArguments args) {
        repository.conferirSchema();
        log.info("Conexão e tabelas do schema configuracao verificadas.");
    }
}
