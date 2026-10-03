package com.redxela.deadlock;

import static com.redxela.domain.ResourceType.LOADING_DOCK;
import static com.redxela.domain.ResourceType.SCANNER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.redxela.domain.ResourceType;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PreventionModeTest {

    private static final List<ResourceType> TRUCK_NATURAL_ORDER = List.of(LOADING_DOCK, SCANNER);

    @Test
    void without_prevention_the_natural_order_is_kept_and_it_is_the_reverse_of_the_global_one() {
        assertThat(PreventionMode.NONE.arrange(TRUCK_NATURAL_ORDER)).containsExactly(LOADING_DOCK, SCANNER);
        assertThat(SCANNER.rank()).isLessThan(LOADING_DOCK.rank()); // por eso andén → escáner es "al revés"
    }

    @Test
    void the_global_order_sorts_by_resource_rank_which_removes_the_circular_wait() {
        assertThat(PreventionMode.GLOBAL_ORDER.arrange(TRUCK_NATURAL_ORDER)).containsExactly(SCANNER, LOADING_DOCK);
    }

    @Test
    void arranging_never_modifies_the_list_it_receives() {
        List<ResourceType> natural = new ArrayList<>(TRUCK_NATURAL_ORDER);

        PreventionMode.GLOBAL_ORDER.arrange(natural);

        assertThat(natural).containsExactly(LOADING_DOCK, SCANNER);
    }

    @Test
    void the_global_order_is_idempotent_for_an_order_that_already_complies() {
        List<ResourceType> ascending = List.of(ResourceType.FORKLIFT, ResourceType.PACKING_STATION, SCANNER);

        assertThat(PreventionMode.GLOBAL_ORDER.arrange(ascending)).containsExactlyElementsOf(ascending);
    }

    @Test
    void every_mode_explains_itself_to_the_user() {
        for (PreventionMode mode : PreventionMode.values()) {
            assertThat(mode.displayName()).isNotBlank();
            assertThat(mode.description()).isNotBlank();
        }
    }

    @Test
    void prevention_is_on_by_default_so_the_system_cannot_freeze_by_accident() {
        AcquisitionPolicy policy = new AcquisitionPolicy();

        assertThat(policy.mode()).isEqualTo(PreventionMode.GLOBAL_ORDER);
        assertThat(policy.arrange(TRUCK_NATURAL_ORDER)).containsExactly(SCANNER, LOADING_DOCK);
    }

    @Test
    void the_policy_can_be_switched_at_runtime() {
        AcquisitionPolicy policy = new AcquisitionPolicy();

        policy.setMode(PreventionMode.NONE);

        assertThat(policy.arrange(TRUCK_NATURAL_ORDER)).containsExactly(LOADING_DOCK, SCANNER);
        assertThatThrownBy(() -> policy.setMode(null)).isInstanceOf(NullPointerException.class);
    }
}
