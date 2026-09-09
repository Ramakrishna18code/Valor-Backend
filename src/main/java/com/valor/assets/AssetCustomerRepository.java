package com.valor.assets;
import com.valor.auth.CustomerProfile;
import java.util.Optional;
import org.springframework.data.repository.Repository;
interface AssetCustomerRepository extends Repository<CustomerProfile,Long>{Optional<CustomerProfile> findByUserId(Long id);}
