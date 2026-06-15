package com.yunmo.domain.repository;

import com.yunmo.domain.entity.Career;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CareerRepository extends JpaRepository<Career, String> {
    List<Career> findByNovelId(String novelId);
}
