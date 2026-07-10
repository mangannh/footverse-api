package com.footverse.address.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.footverse.address.entity.Address;

/**
 * Data access for {@link Address}. Standard CRUD is inherited from {@link JpaRepository}; the
 * user-scoped reads below serve {@code AddressService}, which is the only caller.
 */
public interface AddressRepository extends JpaRepository<Address, Long> {

    /**
     * Returns every address owned by a user.
     *
     * @param userId the owning user id
     * @return the user's addresses (empty when they have none)
     */
    List<Address> findByUserId(Long userId);

    /**
     * Returns an address only when it belongs to the given user, so the service can resolve and
     * ownership-check in one read.
     *
     * @param id     the address id
     * @param userId the owning user id
     * @return the address, or empty when it does not exist or belongs to another user
     */
    Optional<Address> findByIdAndUserId(Long id, Long userId);

    /**
     * Returns the user's current default address. At most one exists, by the service-enforced
     * exactly-one-default-per-user invariant.
     *
     * @param userId the owning user id
     * @return the default address, or empty when the user has no address
     */
    Optional<Address> findByUserIdAndIsDefaultTrue(Long userId);

    /**
     * Counts the addresses owned by a user.
     *
     * @param userId the owning user id
     * @return the number of addresses
     */
    long countByUserId(Long userId);
}
