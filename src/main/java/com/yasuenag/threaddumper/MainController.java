/*
 * Copyright (C) 2019 Yasumasa Suenaga
 *
 * This file is part of ThreadDumper.
 *
 * UL Viewer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ThreadDumper is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with ThreadDumper.  If not, see <http://www.gnu.org/licenses/>.
*/
package com.yasuenag.threaddumper;

import com.yasuenag.threaddumper.parser.ThreadDump;
import com.yasuenag.threaddumper.parser.ThreadDumpParser;
import com.yasuenag.threaddumper.parser.ThreadInfo;
import java.io.File;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Callback;

public class MainController implements Initializable {
    
    private static class ThreadInfoForView{
        
        private final int nid;
        
        private final String name;
        
        private final List<ThreadInfo> threads;
        
        private final double cpu;
        
        private final long allocated;
        
        private final boolean stuck;
        
        public ThreadInfoForView(int nid, List<ThreadInfo> threads){
            this.nid = nid;
            this.name = threads.get(0).getName();
            this.threads = threads;
            
            var top = threads.get(0);
            var tail = threads.get(threads.size() - 1);
            
            this.cpu = (top.getCpu().isPresent() && tail.getCpu().isPresent()) ? (tail.getCpu().getAsDouble() - top.getCpu().getAsDouble()) : -1.0d;
            this.allocated = (top.getAllocated().isPresent() && tail.getAllocated().isPresent()) ? (tail.getAllocated().getAsLong() - top.getAllocated().getAsLong()) : -1;
            
            if(threads.size() > 1){
                this.stuck = threads.stream()
                                    .skip(1) // skip top element
                                    .allMatch(t -> Objects.equals(t.getCallFrames(), top.getCallFrames()));
            }
            else{
                this.stuck = false;
            }

        }
        
        public int getNid(){
            return nid;
        }
        
        public String getName(){
            return name;
        }
        
        public List<ThreadInfo> getThreads(){
            return threads;
        }
        
        public double getCpu(){
            return cpu;
        }

        public long getAllocated(){
            return allocated;
        }
        
        public boolean isStuck(){
            return stuck;
        }
        
        @Override
        public String toString() {
            String result = name + " (nid=" + nid;
            
            if(cpu >= 0.0f){
                result += ", cpu=" + cpu;
            }
            if(allocated >= 0){
                result += ", allocated=" + allocated;
            }
            
            result += ")";
            
            if(stuck){
                result += "  STUCK!";
            }
            
            return result;
        }
        
    }
    
    private static class TimedTableColumn extends TableColumn<Map<LocalDateTime, ThreadInfo.CallFrame>, String> implements Callback<TableColumn.CellDataFeatures<Map<LocalDateTime, ThreadInfo.CallFrame>, String>, ObservableValue<String>>{
        
        private final LocalDateTime time;
        
        public TimedTableColumn(LocalDateTime time){
            super(time.toString());
            this.time = time;
            this.setCellValueFactory(this);
        }

        @Override
        public ObservableValue<String> call(CellDataFeatures<Map<LocalDateTime, ThreadInfo.CallFrame>, String> p) {
            var frame = p.getValue().get(time);
            return new ReadOnlyObjectWrapper<>((frame == null) ? "" : frame.getStackTraceElement().toString());
        }
        
    }
    
    private Stage stage;
    
    @FXML
    private ToggleGroup sortOrder;
    
    @FXML
    private ToggleGroup sortDirection;
    
    @FXML
    private RadioButton sortDirectionDESC;
    
    @FXML
    private ListView<ThreadInfoForView> threadList;
    
    @FXML
    private TableView<Map<LocalDateTime, ThreadInfo.CallFrame>> timeseriesTable;
    
    private List<ThreadDump> dumps;
    
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        threadList.getSelectionModel().selectedItemProperty().addListener(this::onThreadChanged);
        sortOrder.selectedToggleProperty().addListener(this::onRadioButtonChanged);
        sortDirection.selectedToggleProperty().addListener(this::onRadioButtonChanged);
    }
    
    private void onThreadChanged(ObservableValue<? extends ThreadInfoForView> observable, ThreadInfoForView oldValue, ThreadInfoForView newValue){
        
        if(newValue == null){
            timeseriesTable.getItems().clear();
            return;
        }
        
        var maxStacks = newValue.getThreads().get(0).getCallFrames().size();
        ObservableList<Map<LocalDateTime, ThreadInfo.CallFrame>> stacks = FXCollections.observableArrayList();
        
        for(int i = 0; i < maxStacks; i++){
            Map<LocalDateTime, ThreadInfo.CallFrame> callStackMap = new HashMap<>();
            for(var info : newValue.getThreads()){
                callStackMap.put(info.getTime(), info.getCallFrames().get(i));
            }
            stacks.add(callStackMap);
        }
        
        timeseriesTable.setItems(stacks);
    }
    
    private void onRadioButtonChanged(ObservableValue<? extends Toggle> observable, Toggle oldValue, Toggle newValue){
        String order = sortOrder.getSelectedToggle().getUserData().toString();
        
        Comparator<ThreadInfoForView> comparator = switch(order){
            case "nid" -> Comparator.comparingInt(ThreadInfoForView::getNid);
            case "threadName" -> Comparator.comparing(ThreadInfoForView::getName);
            case "cpuTime" -> Comparator.comparingDouble(ThreadInfoForView::getCpu);
            case "memoryAllocation" -> Comparator.comparingLong(ThreadInfoForView::getAllocated);
            default -> throw new UnsupportedOperationException("Unknown toggle group data: " + order);
        };
        
        if(sortDirectionDESC.isSelected()){
            comparator = comparator.reversed();
        }
        
        threadList.getItems().sort(comparator);
    }
    
    public void setStage(Stage stage){
        this.stage = stage;
    }
    
    @FXML
    private void onOpenClicked(ActionEvent event) {
        var dialog = new FileChooser();
        dialog.setTitle("Open thread dumps");
        
        var files = dialog.showOpenMultipleDialog(stage);
        if(files == null){
            return;
        }
        
        dumps = ThreadDumpParser.parse(files.stream()
                                            .map(File::toPath)
                                            .collect(Collectors.toList()));
        
        timeseriesTable.getItems().clear();
        timeseriesTable.getColumns()
                       .clear();
        timeseriesTable.getColumns()
                       .addAll(dumps.stream()
                                    .map(d -> new TimedTableColumn(d.getTime()))
                                    .collect(Collectors.toList()));
        
        var nidMap = dumps.stream()
                          .flatMap(d -> d.getThreads().stream())
                          .sorted(Comparator.comparing(ThreadInfo::getTime))
                          .collect(Collectors.groupingBy(ThreadInfo::getNid));
        
        for(var info : nidMap.values()){
            var maxStacks = info.stream()
                                .mapToInt(t -> t.getCallFrames().size())
                                .max()
                                .getAsInt();
            
            for(var thread : info){
                while(thread.getCallFrames().size() != maxStacks){
                    thread.getCallFrames().addFirst(null);
                }
            }
            
        }
        
        threadList.setItems(nidMap.entrySet()
                                  .stream()
                                  .map(e -> new ThreadInfoForView(e.getKey(), e.getValue()))
                                  .sorted(Comparator.comparing(ThreadInfoForView::getNid))
                                  .collect(FXCollections::observableArrayList, List::add, List::addAll));
        onRadioButtonChanged(null, null, null);
    }
    
    @FXML
    private void onCloseClicked(ActionEvent event) {
        Platform.exit();
    }
    
}
