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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 *
 * @author yasuenag
 */
public class ThreadDumpParser {
    
    // yyyy-mm-dd HH:MM:ss
    private static final Pattern DATETIME_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}$");

    private static final Pattern THREAD_LIST_ADDR_PATTERN = Pattern.compile("^_java_thread_list=(0x[0-9a-f]+),.*$");

    private static final Pattern THREAD_INFO_PATTERN = Pattern.compile("^\"(?<name>.+)\"( #(?<id>\\d+))?(?<daemon> daemon)?( prio=(?<prio>\\d+))?( os_prio=(?<osPrio>\\d+))?( cpu=(?<cpu>[0-9\\.]+)ms elapsed=(?<elapsed>[0-9\\.]+)s)?( allocated=(?<allocated>[0-9BKMG]+))?( defined_classes=(?<definedClasses>\\d+))? tid=(?<tid>0x[0-9a-f]+) nid=(?<nid>0x[0-9a-f]+) (?<state>.+?)(\\s+\\[(?<lastJavaSP>0x[0-9a-f]+)\\])?$");

    private static final Pattern THREAD_STATE_PATTERN = Pattern.compile("^\\s+java\\.lang\\.Thread\\.State: (?<threadState>[A-Z_]+)( \\((?<threadStateDescription>.+)\\))?$");
  
    private static final Pattern CALL_FRAME_PATTERN = Pattern.compile("^\\s+at (?<declaringClass>.+)\\.(?<methodName>.+)\\(((?<moduleName>.+)@(?<moduleVersion>.+)/)?(?<fileName>.+?)(:(?<lineNumber>\\d+))?\\)$");

    private static final Pattern LOCK_PATTERN = Pattern.compile("^\\s+- (?<description>.+) <(?<address>0x[0-9a-f]+)> \\(a (?<lockClass>.+)\\)$");

    private static final Pattern REFS_PATTERN = Pattern.compile("^JNI global refs: (?<jniGlobalRefs>\\d+), weak refs: (?<weakRefs>\\d+)$");

    public static List<ThreadDump> parse(List<Path> files){
        return files.stream()
                    .map(ThreadDumpParser::parseEachFile)
                    .flatMap(List::stream)
                    .collect(Collectors.toList());
    }

    public static List<ThreadDump> parseEachFile(Path file){
        List<ThreadDump> result = new ArrayList<>();

        try(var reader = Files.newBufferedReader(file)){
            ThreadDump dump;
            while((dump = parseThreadDump(reader)) != null){
                result.add(dump);
            }

            return result;
        }
        catch(IOException e){
            throw new UncheckedIOException(e);
        }

    }

    private static ThreadDump parseThreadDump(BufferedReader reader) throws IOException{
        String line;

        // Seek beginning of thread dump
        do{
            line = reader.readLine();
        }while((line != null) && !DATETIME_PATTERN.matcher(line).matches());

        if(line == null){
            return null;
        }

        var datetime = LocalDateTime.parse(line.replace(' ', 'T'));
        ThreadDump result = new ThreadDump(datetime);

        line = reader.readLine();
        result.setVmVersion(line.substring(17, line.length() - 1)); // chomp banner string

        line = skipEmptyLine(reader);
        if(line == null){
            return null;
        }
        else if(line.equals("Threads class SMR info:")){
            result.setSMRInfo(parseSMRInfo(reader));
            line = skipEmptyLine(reader);

            if(line == null){
                return result;
            }

        }

        ThreadInfo threadInfo;
        while((threadInfo = parseThread(datetime, line, reader)) != null){
            result.getThreads().add(threadInfo);

            line = reader.readLine();
            var refsMatcher = REFS_PATTERN.matcher(line);
            if(refsMatcher.matches()){
                result.setJniGlobalRefs(Integer.parseInt(refsMatcher.group("jniGlobalRefs")));
                result.setWeakRefs(Integer.parseInt(refsMatcher.group("weakRefs")));
                break;
            }

        }

        return result;
    }

    public static String skipEmptyLine(BufferedReader reader) throws IOException{
        String line;

        while((line = reader.readLine()) != null){
            if(!line.isEmpty()){
                break;
            }
        }

        return line;
    }
    
    public static ThreadDump.SMRInfo parseSMRInfo(BufferedReader reader) throws IOException{
        String line;

        line = reader.readLine();
        if(line == null){
            return null;
        }
        var addrMatcher = THREAD_LIST_ADDR_PATTERN.matcher(line);
        if(!addrMatcher.matches()){
            return null;
        }
        var info = new ThreadDump.SMRInfo(Long.decode(addrMatcher.group(1)));

        var sb = new StringBuilder();
        while((line = reader.readLine()) != null){

            if(line.equals("}")){
                break;
            }

            sb.append(line.replace(',', '\n'));
        }
        
        info.setJavaThreads(sb.toString()
                              .lines()
                              .map(String::trim)
                              .map(Long::decode)
                              .collect(Collectors.toList()));

        return info;
    }

    public static ThreadInfo parseThread(LocalDateTime time, String firstLine, BufferedReader reader) throws IOException{
        var headerMatcher = THREAD_INFO_PATTERN.matcher(firstLine);
        if(!headerMatcher.matches()){
            return null;
        }
        var info = new ThreadInfo(time,
                                  headerMatcher.group("name"),
                                  Long.decode(headerMatcher.group("tid")),
                                  Integer.decode(headerMatcher.group("nid")),
                                  headerMatcher.group("state"));
        if(headerMatcher.group("daemon") != null){
            info.setDaemon(true);
        }
        if(headerMatcher.group("prio") != null){
            info.setPrio(Integer.parseInt(headerMatcher.group("prio")));
        }
        if(headerMatcher.group("osPrio") != null){
            info.setOsPrio(Integer.parseInt(headerMatcher.group("osPrio")));
        }
        if(headerMatcher.group("cpu") != null){
            info.setCpu(Double.parseDouble(headerMatcher.group("cpu")));
        }
        if(headerMatcher.group("elapsed") != null){
            info.setElapsed(Double.parseDouble(headerMatcher.group("elapsed")));
        }
        if(headerMatcher.group("allocated") != null){
            info.setAllocated(convertToBytes(headerMatcher.group("allocated")));
        }
        if(headerMatcher.group("definedClasses") != null){
            info.setDefinedClasses(Long.parseLong(headerMatcher.group("definedClasses")));
        }
        if(headerMatcher.group("lastJavaSP") != null){
            info.setLastJavaSP(Long.decode(headerMatcher.group("lastJavaSP")));
        }

        String line;
        line = reader.readLine();
        if(line == null){
            return null;
        }
        
        var threadStateMatcher = THREAD_STATE_PATTERN.matcher(line);
        if(threadStateMatcher.matches()){
            info.setThreadState(Thread.State.valueOf(threadStateMatcher.group("threadState")));

            var desc = threadStateMatcher.group("threadStateDescription");
            if(desc != null){
                info.setThreadStateDescription(desc);
            }

        }
        else{
            return info;
        }

        LinkedList<ThreadInfo.CallFrame> callFrames = new LinkedList<>();
        ThreadInfo.CallFrame currentFrame = null;
        while((line = reader.readLine()) != null){

            if(line.isEmpty()){
                break;
            }

            var callFrameMatcher = CALL_FRAME_PATTERN.matcher(line);
            if(callFrameMatcher.matches()){
                String fileName;
                int lineNumber;

                var matchedLineNumber = callFrameMatcher.group("lineNumber");
                if(matchedLineNumber == null){
                    fileName = null;
                    lineNumber = -2; // "-2" means native method. See https://docs.oracle.com/en/java/javase/12/docs/api/java.base/java/lang/StackTraceElement.html#%3Cinit%3E(java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,java.lang.String,int)
                }
                else{
                    fileName = callFrameMatcher.group("fileName");
                    lineNumber = Integer.parseInt(matchedLineNumber);
                }
                currentFrame = new ThreadInfo.CallFrame(callFrameMatcher.group("declaringClass"),
                                                        callFrameMatcher.group("moduleName"),
                                                        callFrameMatcher.group("moduleVersion"),
                                                        callFrameMatcher.group("methodName"),
                                                        fileName,
                                                        lineNumber);
                callFrames.add(currentFrame);
                continue;
            }

            var lockMatcher = LOCK_PATTERN.matcher(line);
            if(lockMatcher.matches()){
                var lock = new ThreadInfo.LockInfo(lockMatcher.group("description"),
                                                   Long.decode(lockMatcher.group("address")),
                                                   lockMatcher.group("lockClass"));
                currentFrame.setLock(lock);
            }

        }

        info.setCallFrames(callFrames);
        return info;
    }
        
    private static long convertToBytes(String str){
        long number = Long.parseLong(str.substring(0, str.length() - 1));
        String suffix = str.substring(str.length() - 1);

        return switch(suffix){
            case "B" -> number;
            case "K" -> number * 1024;
            case "M" -> number * 1024 * 1024;
            case "G" -> number * 1024 * 1024 * 1024;
            default -> throw new IllegalArgumentException("Could not convert to long: " + str);
        };
    }
    
}
