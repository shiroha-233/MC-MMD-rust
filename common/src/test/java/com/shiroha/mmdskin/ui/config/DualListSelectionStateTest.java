/* 文件职责：验证双列选择状态的去重、迁移与回排语义。 */
package com.shiroha.mmdskin.ui.config;

import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DualListSelectionStateTest {
    @Test
    void shouldFilterAlreadySelectedItemsFromAvailableList() {
        DualListSelectionState<String> state = new DualListSelectionState<>(
                List.of("c", "b", "a"),
                List.of("b"),
                String::equals,
                Comparator.naturalOrder()
        );

        assertEquals(List.of("c", "a"), state.availableItems());
        assertEquals(List.of("b"), state.selectedItems());
    }

    @Test
    void shouldMoveSelectedItemsBackToAvailableUsingComparatorOrder() {
        DualListSelectionState<String> state = new DualListSelectionState<>(
                List.of("c", "a"),
                List.of("b"),
                String::equals,
                Comparator.naturalOrder()
        );

        state.moveSelectedToAvailable(0);

        assertEquals(List.of("a", "b", "c"), state.availableItems());
        assertEquals(List.of(), state.selectedItems());
    }

    @Test
    void shouldMoveAllItemsBetweenLists() {
        DualListSelectionState<String> state = new DualListSelectionState<>(
                List.of("c", "a"),
                List.of("b"),
                String::equals,
                Comparator.naturalOrder()
        );

        state.moveAllToSelected();
        assertEquals(List.of(), state.availableItems());
        assertEquals(List.of("b", "c", "a"), state.selectedItems());

        state.moveAllToAvailable();
        assertEquals(List.of("a", "b", "c"), state.availableItems());
        assertEquals(List.of(), state.selectedItems());
    }
}
