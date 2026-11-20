package ir.mehdiyasinzadeh.aiops.repository;

import ir.mehdiyasinzadeh.aiops.entity.IncidentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IncidentRepository extends JpaRepository<IncidentEntity, Long> {

    Optional<IncidentEntity> findByIncidentSignature(String incidentSignature);

    List<IncidentEntity> findByServiceName(String serviceName);

    List<IncidentEntity> findByStatus(String status);
}
