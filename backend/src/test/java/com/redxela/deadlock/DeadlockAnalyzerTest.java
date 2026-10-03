package com.redxela.deadlock;

import static com.redxela.domain.ResourceType.FORKLIFT;
import static com.redxela.domain.ResourceType.LOADING_DOCK;
import static com.redxela.domain.ResourceType.PACKING_STATION;
import static com.redxela.domain.ResourceType.SCANNER;
import static org.assertj.core.api.Assertions.assertThat;

import com.redxela.deadlock.DeadlockAnalyzer.Analysis;
import com.redxela.deadlock.DeadlockAnalyzer.Participant;
import com.redxela.domain.ResourceType;
import com.redxela.resources.Operation;
import com.redxela.resources.PoolOperations;
import com.redxela.testsupport.FakeOperation;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * El analizador es una función pura: aquí se le dan fotos del estado de los pools y se comprueba qué
 * concluye, sin hilos. Cada caso tiene el nombre de lo que demuestra.
 */
class DeadlockAnalyzerTest {

    private static PoolOperations pool(ResourceType type, int capacity, List<Operation> holders, List<Operation> waiting) {
        return new PoolOperations(type, capacity, holders, waiting);
    }

    private static List<String> idsOf(Analysis analysis) {
        return analysis.deadlocked().stream().map(Participant::id).toList();
    }

    @Test
    void an_idle_system_has_no_deadlock() {
        List<PoolOperations> idle = List.of(pool(SCANNER, 1, List.of(), List.of()), pool(LOADING_DOCK, 2, List.of(), List.of()));

        assertThat(DeadlockAnalyzer.analyze(idle)).isEmpty();
    }

    @Test
    void the_classic_two_operation_deadlock_over_single_unit_resources_is_detected() {
        Operation a = new FakeOperation("A");
        Operation b = new FakeOperation("B");
        // A tiene el escáner y espera el andén; B tiene el andén y espera el escáner. Una unidad de cada uno.
        List<PoolOperations> pools = List.of(
                pool(SCANNER, 1, List.of(a), List.of(b)),
                pool(LOADING_DOCK, 1, List.of(b), List.of(a)));

        Analysis analysis = DeadlockAnalyzer.analyze(pools).orElseThrow();

        assertThat(idsOf(analysis)).containsExactly("A", "B");
        assertThat(analysis.resources()).containsExactly(SCANNER, LOADING_DOCK);
        assertThat(analysis.waiter().id()).isEqualTo("A");
        assertThat(analysis.holder().id()).isEqualTo("B");
    }

    @Test
    void the_redxela_scenario_two_trucks_hold_the_docks_and_an_order_holds_the_scanner() {
        Operation order = new FakeOperation("ORD-0001");
        Operation truck1 = new FakeOperation("TRK-0001");
        Operation truck2 = new FakeOperation("TRK-0002");
        List<PoolOperations> pools = List.of(
                pool(SCANNER, 1, List.of(order), List.of(truck1, truck2)),
                pool(LOADING_DOCK, 2, List.of(truck1, truck2), List.of(order)));

        Analysis analysis = DeadlockAnalyzer.analyze(pools).orElseThrow();

        assertThat(idsOf(analysis)).containsExactly("ORD-0001", "TRK-0001", "TRK-0002");
        // Los dos camiones están bloqueados aunque entre ellos no se esperen: ambos esperan lo que retiene el pedido.
        assertThat(analysis.waiter().id()).isEqualTo("ORD-0001");
        assertThat(analysis.holder().id()).isEqualTo("TRK-0001"); // pareja en conflicto: la de menor id que se espera mutuamente
    }

    @Test
    void a_cycle_in_the_wait_for_graph_is_not_a_deadlock_when_another_unit_is_free() {
        Operation a = new FakeOperation("A");
        Operation b = new FakeOperation("B");
        // A espera el andén que retiene B; B espera el escáner que retiene A: hay ciclo A→B→A.
        // Pero el andén tiene 2 unidades y solo B usa una: A puede tomar la otra, terminar y soltar el escáner.
        List<PoolOperations> pools = List.of(
                pool(SCANNER, 1, List.of(a), List.of(b)),
                pool(LOADING_DOCK, 2, List.of(b), List.of(a)));

        assertThat(DeadlockAnalyzer.analyze(pools)).isEmpty();
    }

    @Test
    void a_three_operation_cycle_is_detected_and_its_first_link_is_the_conflict() {
        Operation a = new FakeOperation("A");
        Operation b = new FakeOperation("B");
        Operation c = new FakeOperation("C");
        // A tiene el escáner y espera el andén (de B); B tiene el andén y espera el montacargas (de C);
        // C tiene el montacargas y espera el escáner (de A). No hay ninguna pareja que se espere mutuamente.
        List<PoolOperations> pools = List.of(
                pool(FORKLIFT, 1, List.of(c), List.of(b)),
                pool(SCANNER, 1, List.of(a), List.of(c)),
                pool(LOADING_DOCK, 1, List.of(b), List.of(a)));

        Analysis analysis = DeadlockAnalyzer.analyze(pools).orElseThrow();

        assertThat(idsOf(analysis)).containsExactly("A", "B", "C");
        assertThat(analysis.waiter().id()).isEqualTo("A");
        assertThat(analysis.holder().id()).isEqualTo("B"); // A espera lo que retiene B
        assertThat(analysis.resources()).containsExactly(FORKLIFT, SCANNER, LOADING_DOCK);
    }

    @Test
    void a_chain_of_waits_that_ends_in_an_operation_that_is_running_is_not_a_deadlock() {
        Operation a = new FakeOperation("A");
        Operation b = new FakeOperation("B");
        // A espera el andén que retiene B, pero B no espera nada: está trabajando y terminará.
        List<PoolOperations> pools = List.of(
                pool(SCANNER, 1, List.of(a), List.of()),
                pool(LOADING_DOCK, 1, List.of(b), List.of(a)));

        assertThat(DeadlockAnalyzer.analyze(pools)).isEmpty();
    }

    @Test
    void an_operation_that_only_waits_without_holding_anything_is_not_part_of_the_deadlock() {
        Operation a = new FakeOperation("A");
        Operation b = new FakeOperation("B");
        Operation bystander = new FakeOperation("C");
        List<PoolOperations> pools = List.of(
                pool(SCANNER, 1, List.of(a), List.of(b, bystander)),
                pool(LOADING_DOCK, 1, List.of(b), List.of(a)));

        Analysis analysis = DeadlockAnalyzer.analyze(pools).orElseThrow();

        assertThat(idsOf(analysis)).containsExactly("A", "B"); // C solo espera: queda atrás, pero no forma parte
    }

    @Test
    void an_operation_blocked_behind_a_deadlock_while_holding_something_is_reported_with_it() {
        Operation a = new FakeOperation("A");
        Operation b = new FakeOperation("B");
        Operation stuck = new FakeOperation("C");
        // C retiene una estación de empaque que nadie pide y espera el escáner que retiene A (en deadlock con B).
        List<PoolOperations> pools = List.of(
                pool(PACKING_STATION, 6, List.of(stuck), List.of()),
                pool(SCANNER, 1, List.of(a), List.of(b, stuck)),
                pool(LOADING_DOCK, 1, List.of(b), List.of(a)));

        Analysis analysis = DeadlockAnalyzer.analyze(pools).orElseThrow();

        assertThat(idsOf(analysis)).containsExactly("A", "B", "C");
        // La estación de empaque que retiene C no se disputa: nadie del conflicto la espera.
        assertThat(analysis.resources()).containsExactly(SCANNER, LOADING_DOCK);
        assertThat(analysis.waiter().id()).isEqualTo("A"); // la pareja en conflicto sigue siendo la del ciclo
    }

    @Test
    void a_resource_that_is_held_but_that_nobody_waits_for_is_not_part_of_the_conflict() {
        Operation order = new FakeOperation("ORD-1");
        Operation truck = new FakeOperation("TRK-1");
        // Como en la aplicación: el pedido también retiene siempre un encargado, que nadie espera.
        List<PoolOperations> pools = List.of(
                pool(ResourceType.WAREHOUSE_WORKER, 10, List.of(order), List.of()),
                pool(SCANNER, 1, List.of(order), List.of(truck)),
                pool(LOADING_DOCK, 1, List.of(truck), List.of(order)));

        Analysis analysis = DeadlockAnalyzer.analyze(pools).orElseThrow();

        assertThat(analysis.resources()).containsExactly(SCANNER, LOADING_DOCK);
        assertThat(analysis.deadlocked().get(0).held()).contains(ResourceType.WAREHOUSE_WORKER, SCANNER); // lo retiene, sí
    }

    @Test
    void operations_that_are_working_never_count_even_if_others_are_stuck_nearby() {
        Operation a = new FakeOperation("A");
        Operation b = new FakeOperation("B");
        Operation working = new FakeOperation("W");
        List<PoolOperations> pools = List.of(
                pool(PACKING_STATION, 6, List.of(working), List.of()),
                pool(SCANNER, 1, List.of(a), List.of(b)),
                pool(LOADING_DOCK, 1, List.of(b), List.of(a)));

        assertThat(idsOf(DeadlockAnalyzer.analyze(pools).orElseThrow())).doesNotContain("W");
    }

    @Test
    void the_same_deadlock_always_has_the_same_key_whatever_the_order_of_the_operations() {
        Operation a = new FakeOperation("A");
        Operation b = new FakeOperation("B");
        List<PoolOperations> pools = List.of(
                pool(SCANNER, 1, List.of(a), List.of(b)),
                pool(LOADING_DOCK, 1, List.of(b), List.of(a)));
        List<PoolOperations> reversed = List.of(
                pool(LOADING_DOCK, 1, List.of(b), List.of(a)),
                pool(SCANNER, 1, List.of(a), List.of(b)));

        assertThat(DeadlockAnalyzer.analyze(pools).orElseThrow().key())
                .isEqualTo(DeadlockAnalyzer.analyze(reversed).orElseThrow().key())
                .isEqualTo("A,B");
    }

    @Test
    void a_different_set_of_blocked_operations_is_a_different_deadlock() {
        Operation a = new FakeOperation("A");
        Operation b = new FakeOperation("B");
        Operation c = new FakeOperation("C");
        Operation d = new FakeOperation("D");
        Optional<Analysis> first = DeadlockAnalyzer.analyze(List.of(
                pool(SCANNER, 1, List.of(a), List.of(b)),
                pool(LOADING_DOCK, 1, List.of(b), List.of(a))));
        Optional<Analysis> second = DeadlockAnalyzer.analyze(List.of(
                pool(SCANNER, 1, List.of(c), List.of(d)),
                pool(LOADING_DOCK, 1, List.of(d), List.of(c))));

        // El detector usa esta clave para distinguir "el mismo deadlock que persiste" de "otro deadlock".
        assertThat(first.orElseThrow().key()).isEqualTo("A,B");
        assertThat(second.orElseThrow().key()).isEqualTo("C,D");
    }
}
