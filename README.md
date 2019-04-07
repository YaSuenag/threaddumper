# ThreadDumper

ThreadDumper is time-based thread dump analyzer.  
You can see all call stacks of a thread in multiple thread dumps.

## How to use

### Start application

#### Linux x64

```
$ cd threaddumper-<version>-linux-amd64/bin
$ ./threaddumper
```

#### Windows x64

* Run `threaddumper.bat`

### Open thread dump(s)

You can open several thread dump from [File] -> [Open] menu.

### Check thread dumps

* Upper window shows all threads in all thread dumps.
* "STUCK!" labes will be added if the thread has same call stacks in all thread dumps.
* You can sort thread names by nid, thread name, CPU time, memory allocation.
    * CPU time and memory allocation are introduced in JDK 11.
* Lower table shows all call stacks in selected thread.
    * The columns shows call stack in each thread dumps.

## License

The GNU Lesser General Public License, version 3.0
