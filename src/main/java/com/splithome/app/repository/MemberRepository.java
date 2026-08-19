package com.splithome.app.repository;
import com.splithome.app.model.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface MemberRepository extends JpaRepository<Member, Long> {
    List<Member> findByHouseholdIdOrderByIdAsc(Long householdId);
    List<Member> findByHouseholdIdAndActiveTrueOrderByIdAsc(Long householdId);
    boolean existsByHouseholdIdAndActiveTrueAndNameIgnoreCase(Long householdId, String name);
}
