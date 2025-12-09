package com.duom.ardabiomeseditor.ui.views;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TablePosition;
import javafx.scene.control.TableView;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Custom selection model for a TableView that allows for advanced selection handling,
 * such as selecting entire rows, columns, or specific cells.
 *
 * @param <S> The type of the objects contained within the TableView.
 */
public class BiomeTableViewSelectionModel<S> extends TableView.TableViewSelectionModel<S> {

    private final TableView<S> tableView;
    private Set<TablePosition<S,?>> selectedCells;

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

        return FXCollections.observableArrayList(selectedCells);
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
        int colIndex = tableView.getColumns().indexOf(column);
        return selectedCells.stream()
                .anyMatch(pos -> pos.getRow() == row &&
                        tableView.getColumns().indexOf(pos.getTableColumn()) == colIndex);
    }

    /**
     * Selects a specific cell, row, or column.
     *
     * @param row    The row index of the cell to select, or -1 to select an entire column.
     * @param column The TableColumn to select, or null to select an entire row.
     */
    @Override
    public void select(int row, TableColumn<S, ?> column) {

        // Select specific cell
        if (column != null && row != -1) {

            selectedCells.add(new TablePosition<>(tableView, row, column));

        } else if (column != null) {

            // Select entire column
            for (int rowIndex = 0; rowIndex < tableView.getItems().size(); rowIndex++) {

                selectedCells.add(new TablePosition<>(tableView, rowIndex, column));
            }
        } else {

            // Select entire row
            for (int columnIndex = 1; columnIndex < tableView.getColumns().size(); columnIndex++) {

                selectedCells.add(new TablePosition<>(tableView, row, tableView.getColumns().get(columnIndex)));
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

            selectedCells.remove(new TablePosition<>(tableView, row, column));
        } else {

            for (int columnIndex = 1; columnIndex < tableView.getColumns().size(); columnIndex++) {

                selectedCells.remove(new TablePosition<>(tableView, row, tableView.getColumns().get(columnIndex)));
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
    public Set<TablePosition<S,?>> getSelectedColumns() {

        int numberOfRows = tableView.getItems().size();
        Set<TablePosition<S,?>> selectedColumns = new HashSet<>();
        Map<Integer, Set<TablePosition<S,?>>> columnPositions = new HashMap<>();

        if (numberOfRows == 0) {
            return selectedColumns;
        }

        for (TablePosition<S, ?> pos : selectedCells) {
            int colIndex = tableView.getColumns().indexOf(pos.getTableColumn());
            columnPositions.computeIfAbsent(colIndex, k -> new HashSet<>()).add(pos);
        }

        // Check which columns are fully selected and add all their positions
        for (Map.Entry<Integer, Set<TablePosition<S,?>>> entry : columnPositions.entrySet()) {
            if (entry.getValue().size() == numberOfRows) {
                selectedColumns.addAll(entry.getValue());
            }
        }

        return selectedColumns;
    }

    /**
     * Retrieves the indices of fully selected columns.
     *
     * @return A set of integers representing the indices of fully selected columns.
     */
    public Set<Integer> getSelectedColumnIndices() {

        Set<Integer> selectedColumnIndices = new HashSet<>();

        for (TablePosition<S, ?> pos : getSelectedColumns()) {
            int colIndex = tableView.getColumns().indexOf(pos.getTableColumn());
            selectedColumnIndices.add(colIndex);
        }

        return selectedColumnIndices;
    }

    /**
     * Checks if a specific column is fully selected.
     *
     * @param columnIndex The index of the column to check.
     * @return True if the column is fully selected, false otherwise.
     */
    public boolean isColumnSelected(int columnIndex) {

        var numberOfRows = tableView.getItems().size();

        for (TablePosition<S, ?> pos : selectedCells) {
            if (tableView.getColumns().indexOf(pos.getTableColumn()) == columnIndex) {
                numberOfRows--;
            }
        }

        return numberOfRows==0;
    }

    /**
     * Selects an entire column by its index.
     *
     * @param columnIndex The index of the column to select.
     */
    public void selectColumn(int columnIndex) {
        select(-1, tableView.getColumns().get(columnIndex));
    }

    /**
     * Deselects an entire column by its index.
     *
     * @param columnIndex The index of the column to deselect.
     */
    public void deselectColumn(int columnIndex) {
        for (int rowIndex = 0; rowIndex < tableView.getItems().size(); rowIndex++) {
            clearSelection(rowIndex, tableView.getColumns().get(columnIndex));
        }
    }

    /**
     * Selects a specific cell by its row and column indices.
     *
     * @param rowIndex    The row index of the cell to select.
     * @param columnIndex The column index of the cell to select.
     */
    public void selectCell(int rowIndex, int columnIndex) {
        clearSelection();
        select(rowIndex, tableView.getColumns().get(columnIndex));
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