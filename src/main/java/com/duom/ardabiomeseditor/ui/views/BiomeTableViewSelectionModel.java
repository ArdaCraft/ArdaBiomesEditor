package com.duom.ardabiomeseditor.ui.views;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TableView;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Custom selection model for a TableView that allows for advanced selection handling,
 * such as selecting entire rows, columns, or specific cells.
 *
 * @param <S> The type of the objects contained within the TableView.
 */
public class BiomeTableViewSelectionModel<S> extends TableView.TableViewSelectionModel<S> {

    private final TableView<S> tableView;

    private record CellIdentifier(int row, String modifierName) {}
    private Set<CellIdentifier> selectedCells;

    public BiomeTableViewSelectionModel(TableView<S> tableView) {
        super(tableView);
        this.tableView = tableView;
        this.selectedCells = new HashSet<>();
    }

    /**
     * Retrieves the currently selected cells.
     *
     * @return An observable list of selected TablePosition objects.
     */
    @Override
    public ObservableList<TablePosition> getSelectedCells() {

        ObservableList<TablePosition> positions = FXCollections.observableArrayList();

        for (CellIdentifier cell : selectedCells) {
            // Find the column with matching modifier name from UserData
            tableView.getColumns().stream()
                    .filter(col -> cell.modifierName().equals(col.getUserData()))
                    .findFirst().ifPresent(column -> positions.add(new TablePosition<>(tableView, cell.row(), column)));

        }

        return positions;
    }

    /**
     * Checks if a specific cell is selected.
     *
     * @param row    The row index of the cell.
     * @param column The TableColumn of the cell.
     * @return True if the cell is selected, false otherwise.
     */
    @Override
    public boolean isSelected(int row, TableColumn<S, ?> column) {

        String modifierName = column != null ? (String) column.getUserData() : "";

        return selectedCells.contains(new CellIdentifier(row, modifierName));
    }

    /**
     * Selects a specific cell, row, or column.
     *
     * @param row    The row index of the cell to select, or -1 to select an entire column.
     * @param column The TableColumn to select, or null to select an entire row.
     */
    @Override
    public void select(int row, TableColumn<S, ?> column) {

        String modifierName = column != null ? (String) column.getUserData() : "";

        // Select specific cell
        if (column != null && row != -1) {

            selectedCells.add(new CellIdentifier(row, modifierName));

        } else if (column != null) {

            // Select entire column
            for (int rowIndex = 0; rowIndex < tableView.getItems().size(); rowIndex++) {

                selectedCells.add(new CellIdentifier(rowIndex, modifierName));
            }
        } else {

            // Select entire row
            for (int columnIndex = 1; columnIndex < tableView.getColumns().size(); columnIndex++) {

                var currentColumn = tableView.getColumns().get(columnIndex);
                String currentColumnModifierName = currentColumn != null ? (String) currentColumn.getUserData() : "";
                selectedCells.add(new CellIdentifier(row, currentColumnModifierName));
            }
        }
    }

    /**
     * Clears the current selection and selects a specific cell, row, or column.
     *
     * @param row    The row index of the cell to select.
     * @param column The TableColumn to select.
     */
    @Override
    public void clearAndSelect(int row, TableColumn<S, ?> column) {
        clearSelection();
        select(row, column);
    }

    /**
     * Clears the selection for a specific cell, row, or column.
     *
     * @param row    The row index of the cell to deselect.
     * @param column The TableColumn to deselect.
     */
    @Override
    public void clearSelection(int row, TableColumn<S, ?> column) {

        if (column != null) {

            String modifierName = (String) column.getUserData();
            selectedCells.remove(new CellIdentifier(row, modifierName));
        } else {

            for (int columnIndex = 1; columnIndex < tableView.getColumns().size(); columnIndex++) {

                var currentColumn = tableView.getColumns().get(columnIndex);
                String currentColumnModifierName = currentColumn != null ? (String) currentColumn.getUserData() : "";
                selectedCells.remove(new CellIdentifier(row, currentColumnModifierName));
            }
        }
    }

    /**
     * Clears all selections.
     */
    @Override
    public void clearSelection() {

        this.selectedCells = new HashSet<>();
    }

    /**
     * Retrieves the set of fully selected columns.
     *
     * @return A set of TablePosition objects representing fully selected columns.
     */
    public Set<String> getSelectedColumns() {

        int numberOfRows = tableView.getItems().size();

        if (numberOfRows == 0) {
            return new HashSet<>();
        }

        // Group selections by modifier name and count how many rows are selected per column
        Map<String, Long> columnSelectionCounts = selectedCells.stream()
                .collect(Collectors.groupingBy(
                        CellIdentifier::modifierName,
                        Collectors.counting()
                ));

        // Return only columns where all rows are selected
        return columnSelectionCounts.entrySet().stream()
                .filter(entry -> entry.getValue() == numberOfRows)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    /**
     * Checks if a specific column is fully selected.
     *
     * @param modifierName The name of the modifier represented by the column.
     * @return True if the column is fully selected, false otherwise.
     */
    public boolean isColumnSelected(String modifierName) {

        int numberOfRows = tableView.getItems().size();
        return selectedCells.stream()
                .filter(cell -> cell.modifierName.equals(modifierName))
                .count() == numberOfRows;
    }

    /**
     * Selects an entire column by its index.
     *
     * @param modifierName The name of the modifier represented by the column.
     */
    public void selectColumn(String modifierName) {

        for (int rowIndex = 0; rowIndex < tableView.getItems().size(); rowIndex++) {
            selectedCells.add(new CellIdentifier(rowIndex, modifierName));
        }
    }

    /**
     * Deselects an entire column by its index.
     *
     * @param modifierName The name of the modifier represented by the column.
     */
    public void deselectColumn(String modifierName) {
        selectedCells.removeIf(cell -> cell.modifierName().equals(modifierName));
    }

    /**
     * Selects a specific cell by its row and column indices.
     *
     * @param rowIndex    The row index of the cell to select.
     * @param column      The column index of the cell to select.
     */
    public void selectCell(int rowIndex, TableColumn<S, ?> column) {
        clearSelection();
        String modifierName = column != null ? (String) column.getUserData() : "";
        selectedCells.add(new CellIdentifier(rowIndex, modifierName));
    }

    @Override
    public void selectLeftCell() {/*Not implemented*/}

    @Override
    public void selectRightCell() {/*Not implemented*/}

    @Override
    public void selectAboveCell() {/*Not implemented*/}

    @Override
    public void selectBelowCell() {/*Not implemented*/}
}