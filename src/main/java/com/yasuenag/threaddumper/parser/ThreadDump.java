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
package com.yasuenag.threaddumper.parser;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 *
 * @author yasuenag
 */
public class ThreadDump {
    
    public static class SMRInfo{
        
        private final long javaThreadList;

        private List<Long> javaThreads;

        public SMRInfo(long javaThreadList){
            this.javaThreadList = javaThreadList;
            this.javaThreads = Collections.emptyList();
        }

        public long getJavaThreadList(){
            return javaThreadList;
        }

        public void setJavaThreads(List<Long> javaThreads){
            this.javaThreads = javaThreads;
        }

        public List<Long> getJavaThreads(){
            return javaThreads;
        }
    
    }
    
    private final LocalDateTime time;
    
    private String vmVersion;

    private Optional<SMRInfo> smrInfo;

    private final List<ThreadInfo> threads;

    private int jniGlobalRefs;

    private int weakRefs;
    
    public ThreadDump(LocalDateTime time){
        this.time = time;
        this.threads = new ArrayList<>();

        vmVersion = null;
        smrInfo = Optional.empty();
        jniGlobalRefs = 0;
        weakRefs = 0;
    }

    public LocalDateTime getTime(){
        return time;
    }

    public void setVmVersion(String vmVersion){
        this.vmVersion = vmVersion;
    }

    public String getVmVersion(){
        return vmVersion;
    }
    
    public void setSMRInfo(SMRInfo smrInfo){
        this.smrInfo = Optional.of(smrInfo);
    }

    public Optional<SMRInfo> getSMRInfo(){
        return smrInfo;
    }

    public List<ThreadInfo> getThreads(){
        return threads;
    }

    public void setJniGlobalRefs(int jniGlobalRefs){
        this.jniGlobalRefs = jniGlobalRefs;
    }

    public int getJniGlobalRefs(){
        return jniGlobalRefs;
    }
    
    public void setWeakRefs(int weakRefs){
        this.weakRefs = weakRefs;
    }

    public int getWeakRefs(){
        return weakRefs;
    }

    @Override
    public String toString() {
        return time.toString() + " (" + threads.size() + " threads)";
    }
    
}
