package com.valor.service;

import com.valor.entity.Amc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface AmcService {
    Amc createAmc(Amc amc);
    Amc renewAmc(Long id, Amc amc);
    void cancelAmc(Long id);
    Amc getAmcById(Long id);
    List<Amc> getAllAmcs();
    Page<Amc> searchAmcs(String term, Pageable pageable);
}
