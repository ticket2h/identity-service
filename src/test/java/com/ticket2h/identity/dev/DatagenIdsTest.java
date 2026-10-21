package com.ticket2h.identity.dev;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Hợp đồng với core-service: cùng giá trị với CoreDataGeneratorIT.shared_ids_match_the_contract bên đó. */
class DatagenIdsTest {

    @Test
    void ids_match_the_contract() {
        assertThat(DatagenIds.customer(1)).hasToString("0d000000-0000-4000-8000-000000000001");
        assertThat(DatagenIds.customer(2_000_000)).hasToString("0d000000-0000-4000-8000-0000001e8480");
        assertThat(DatagenIds.organizer(200)).hasToString("0e000000-0000-4000-8000-0000000000c8");
        assertThat(DatagenIds.organizerAccount(7)).hasToString("0c000000-0000-4000-8000-000000000007");
        assertThat(DatagenIds.staff(600)).hasToString("0f000000-0000-4000-8000-000000000258");
    }
}
