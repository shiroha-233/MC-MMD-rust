package com.shiroha.mmdskin.ui.config;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;

/** 双列选择界面的业务状态，负责可选与已选列表迁移语义。 */
final class DualListSelectionState<T> {
    private final BiPredicate<T, T> matcher;
    private final Comparator<T> availableComparator;
    private final List<T> availableItems;
    private final List<T> selectedItems;

    DualListSelectionState(List<T> availableItems,
                           List<T> selectedItems,
                           BiPredicate<T, T> matcher,
                           Comparator<T> availableComparator) {
        this.availableItems = new ArrayList<>(Objects.requireNonNull(availableItems, "availableItems"));
        this.selectedItems = new ArrayList<>(Objects.requireNonNull(selectedItems, "selectedItems"));
        this.matcher = Objects.requireNonNull(matcher, "matcher");
        this.availableComparator = Objects.requireNonNull(availableComparator, "availableComparator");
        this.availableItems.removeIf(this::isSelected);
    }

    List<T> availableItems() {
        return List.copyOf(availableItems);
    }

    List<T> selectedItems() {
        return List.copyOf(selectedItems);
    }

    int availableCount() {
        return availableItems.size();
    }

    int selectedCount() {
        return selectedItems.size();
    }

    T moveAvailableToSelected(int index) {
        if (!isValidIndex(index, availableItems)) {
            return null;
        }
        T item = availableItems.remove(index);
        selectedItems.add(item);
        return item;
    }

    T moveSelectedToAvailable(int index) {
        if (!isValidIndex(index, selectedItems)) {
            return null;
        }
        T item = selectedItems.remove(index);
        availableItems.add(item);
        availableItems.sort(availableComparator);
        return item;
    }

    void moveAllToSelected() {
        selectedItems.addAll(availableItems);
        availableItems.clear();
    }

    void moveAllToAvailable() {
        availableItems.addAll(selectedItems);
        selectedItems.clear();
        availableItems.sort(availableComparator);
    }

    private boolean isSelected(T available) {
        for (T selected : selectedItems) {
            if (matcher.test(selected, available)) {
                return true;
            }
        }
        return false;
    }

    private boolean isValidIndex(int index, List<T> items) {
        return index >= 0 && index < items.size();
    }
}
