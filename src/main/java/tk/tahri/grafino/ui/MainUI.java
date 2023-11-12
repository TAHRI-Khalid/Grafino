package tk.tahri.grafino.ui;

import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import tk.tahri.grafino.component.*;

public class MainUI extends BorderPane {
    private final Grafcet grafcet = new Grafcet();
    private final VariableTable variableTable = new VariableTable();

    public MainUI() {
        var toolBar = new TopToolBar();
        this.setTop(toolBar);

        grafcet.createInitialStep();
        grafcet.startRender();
        this.setCenter(grafcet);

        var statusBar = new ToolBar();
        statusBar.setBackground(new Background(new BackgroundFill(Component.MAIN_COLOR, null, null)));
        statusBar.setPadding(new Insets(10));
        this.setBottom(statusBar);


        toolBar.setOnStepBtnClick(event -> grafcet.getSelectedComponent().ifPresent(component -> {
            if (component instanceof Transition transition && transition.getLinksToChildren().isEmpty()) {
                grafcet.createStep(transition);
            }
        }));

        toolBar.setOnTransitionBtnClick(event -> grafcet.getSelectedComponent().ifPresent(component -> {
            if (component instanceof Step step && step.getLinksToChildren().isEmpty()) {
                grafcet.createTransition(step);
            }
        }));

        toolBar.setOnAddVariableBtnClick(event -> variableTable.addVariable());
        toolBar.setOnRemoveSelectedVariable(event ->variableTable.removeSelectedVariable());

        toolBar.setOnOrBranchBtnClick(event -> grafcet.getSelectedComponent().ifPresent(component -> {
            if (component instanceof Step step && !step.getLinksToChildren().isEmpty()) {
                grafcet.createTransition(step);
            }
        }));

        toolBar.setOnAndBranchBtnClick(event -> grafcet.getSelectedComponent().ifPresent(component -> {
            if (component instanceof Transition transition && !transition.getLinksToChildren().isEmpty()) {
                grafcet.createStep(transition);
            }
        }));

        toolBar.setOnGrafcetBtnClick(event -> {
            grafcet.startRender();
            this.setCenter(grafcet);
            toolBar.setGrafcetToolsVisible(true);
        });

        toolBar.setOnVariableTableBtnClick(event -> {
            grafcet.stopRender();
            this.setCenter(variableTable);
            toolBar.setGrafcetToolsVisible(false);
        });

        grafcet.setOnSelectionChange(selectedComponent -> {
            if (selectedComponent.isPresent()) {
                switch (selectedComponent.get()) {
                    case Step step -> {
                        var sideProperty = new StepProperty(step,variableTable.getOutputVaribles());
                        this.setRight(sideProperty);
                    }
                    case Transition transition -> {
                        var sideProperty = new TransitionProperty(transition);
                        this.setRight(sideProperty);
                    }
                }
            } else {
                this.setRight(null);
            }
        });

        this.setOnKeyPressed(event -> grafcet.getSelectedComponent().ifPresent(component -> {
            int step = event.isControlDown() ? 5 : 1;
            switch (event.getCode()) {
                case UP -> component.updatePositionAndLinks(new Point2D(component.getX(), component.getY() - step));
                case DOWN -> component.updatePositionAndLinks(new Point2D(component.getX(), component.getY() + step));
                case LEFT -> component.updatePositionAndLinks(new Point2D(component.getX() - step, component.getY()));
                case RIGHT -> component.updatePositionAndLinks(new Point2D(component.getX() + step, component.getY()));
            }
        }));
    }

}
