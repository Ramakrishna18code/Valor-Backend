package com.valor.repository;

import com.valor.entity.Amc;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AmcRepository extends JpaRepository<Amc, Long> {

	@Query("""
			select a from Amc a
			where lower(coalesce(a.amcNumber, '')) like lower(concat('%', :term, '%'))
			   or lower(coalesce(a.plan, '')) like lower(concat('%', :term, '%'))
			   or lower(coalesce(a.coverageDetails, '')) like lower(concat('%', :term, '%'))
			   or lower(coalesce(a.status, '')) like lower(concat('%', :term, '%'))
			   or lower(coalesce(a.lift.name, '')) like lower(concat('%', :term, '%'))
			""")
	Page<Amc> search(String term, Pageable pageable);
}
