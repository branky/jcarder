package com.enea.jcarder.analyzer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.SortedSet;
import java.util.TreeSet;

import com.enea.jcarder.common.LockingContext;
import com.enea.jcarder.common.contexts.ContextFileReader;
import com.enea.jcarder.common.contexts.ContextReaderIfc;
import com.enea.jcarder.common.events.EventFileReader;
import com.enea.jcarder.util.BuildInformation;
import com.enea.jcarder.util.Logger;

import static com.enea.jcarder.common.contexts.ContextFileReader.EVENT_DB_FILE;
import static com.enea.jcarder.common.contexts.ContextFileReader.CONTEXTS_DB_FILE;

/**
 * The main class of the jcarder analyzer.
 */
public final class Analyzer {

    enum OutputMode { INCLUDE_ALL,
                      INCLUDE_CYCLES,
                      INCLUDE_ONLY_MULTI_THREADED_CYCLES };

    /*
     * Cycles with only one thread can never cause a deadlock, but it might be
     * possible that basic tests of a single class are very simplified and
     * use only a single thread where a real program might invoke the methods
     * from several different threads. Therefore single-threaded cycles
     * are also interesting to detect and include by default.
     */
    private OutputMode mOutputMode = OutputMode.INCLUDE_CYCLES;
    private boolean mIncludePackages = false;
    private boolean mPrintDetails = false;

    public static void main(String[] args) throws Exception {
        new Analyzer().start(args);
    }

    public void start(String[] args) throws Exception {
        parseArguments(args);
        LockGraphBuilder graphBuilder = new LockGraphBuilder();

        ContextReaderIfc ras
        = new ContextFileReader(CONTEXTS_DB_FILE);

        EventFileReader.parseFile(EVENT_DB_FILE, graphBuilder);
        printInitiallyLoadedStatistics(graphBuilder.getAllLocks());

        CycleDetector cycleDetector = new CycleDetector();
        cycleDetector.analyzeLockNodes(graphBuilder.getAllLocks());
        printCycleAnalysisStatistics(cycleDetector);

        if (mOutputMode == OutputMode.INCLUDE_ALL) {
            printDetailsIfEnabled(cycleDetector.getCycles(), ras);
            generatGraphvizFileForAllNodes(graphBuilder, ras);
        } else {
            if (mOutputMode == OutputMode.INCLUDE_ONLY_MULTI_THREADED_CYCLES) {
                cycleDetector.removeSingleThreadedCycles();
            }
            if (cycleDetector.getCycles().isEmpty()) {
                System.out.println("No cycles found!");
                return;
            }
            graphBuilder.clear(); // Help GC.
            // TODO Also clear all references in LockNode.mOutgoingEdges to
            //      avoid keeping references to a lot of LockEdge and LockNode
            //      objects in order to release as much memory as possible for
            //      the memory mapped file?

            // It is not necessary to use the DuplicateEdgeshandler since
            // those duplicates are removed anyway when cycles that
            // are alike are removed.
            cycleDetector.removeAlikeCycles(ras);

            printDetailsIfEnabled(cycleDetector.getCycles(), ras);
            generateGraphvizFilesForCycles(ras, cycleDetector);
        }
    }

    private void generateGraphvizFilesForCycles(ContextReaderIfc ras,
                                                CycleDetector cycleDetector)
    throws IOException {
        System.out.println();
        int index = 0;
        for (HashSet<LockEdge> edges : cycleDetector.mergeCyclesWithIdenticalLocks()) {
            if (index >= 100) {
                System.out.println("Aborting. Too many cycles!");
                break;
            }
            GraphvizGenerator graphvizGenerator = new GraphvizGenerator();
            createGraphvizFile(graphvizGenerator.generate(edges,
                                                  ras,
                                                  mIncludePackages),
                                                  index++);
        }
    }

    private void  printCycleAnalysisStatistics(CycleDetector cycleDetector) {
        System.out.println("\nCycle analysis result: ");
        System.out.println("   Cycles:          "
                           + cycleDetector.getCycles().size());
        System.out.println("   Edges in cycles: "
                           + cycleDetector.getNumberOfEdges());
        System.out.println("   Nodes in cycles: "
                           + cycleDetector.getNumberOfNodes());
        System.out.println("   Max cycle depth: "
                           + cycleDetector.getMaxCycleDepth());
        System.out.println("   Max graph depth: "
                           + cycleDetector.getMaxDepth());
        System.out.println();
    }

    private void generatGraphvizFileForAllNodes(LockGraphBuilder graphBuilder,
                                                ContextReaderIfc ras)
    throws IOException {
        DuplicatedEdgesHandler.mergeDuplicatedEdges(graphBuilder.getAllLocks(),
                                                    ras);
        // TODO print statistics about removed duplicates?
        LinkedList<LockEdge> allEdges = new LinkedList<LockEdge>();
        for (LockNode node : graphBuilder.getAllLocks()) {
            allEdges.addAll(node.getOutgoingEdges());
        }
        GraphvizGenerator graphvizGenerator = new GraphvizGenerator();
        createGraphvizFile(graphvizGenerator.generate(allEdges,
                                                      ras,
                                                      mIncludePackages),
                                                      0);
    }

    private void parseArguments(String[] args) {
        for (String arg : args) {
            if (arg.equals("-all")) {
                mOutputMode = OutputMode.INCLUDE_ALL;
            } else if (arg.equals("--exclude-single-threaded-cycles")) {
                mOutputMode = OutputMode.INCLUDE_ONLY_MULTI_THREADED_CYCLES;
            } else if (arg.equals("finer")) {
                Logger.setFileLogLevel(Logger.FINER);
            } else if (arg.equals("finest")) {
                Logger.setFileLogLevel(Logger.FINEST);
            } else if (arg.equals("--include-packages")) {
                mIncludePackages = true;
            } else if (arg.equals("--print-details")) {
                mPrintDetails = true;
            } else if (arg.equals("-ver")) {
                BuildInformation.printLongBuildInformation();
                System.exit(0);
            } else {
                // TODO Explain each command line option in more detail.
                System.err.println("Invalid command line argument: " + arg);
                System.err.println("Valid arguments are:"
                                   + "\n  -all "
                                   + "\n  --exclude-single-threaded-cycles"
                                   + "\n  --include-packages"
                                   + "\n  --print-details"
                                   + "\n  -ver"
                                   + "\n  -finer -finest (file log levelbug");
                System.exit(-1);
            }
        }
        // TODO Add parameters for filtering (including & excluding) specific
        //      locks and edges for example by specifying thread names,
        //      object classes, method names or packages?
    }


    private void printDetailsIfEnabled(Iterable<Cycle> cycles,
                                       ContextReaderIfc ras) {
        if (!mPrintDetails) {
            return;
        }
        SortedSet<String> threads = new TreeSet<String>();
        SortedSet<String> methods = new TreeSet<String>();
        for (Cycle cycle : cycles) {
            for (LockEdge edge : cycle.getEdges()) {
                LockingContext source = ras.readContext(edge.getSourceLockingContextId());
                LockingContext target = ras.readContext(edge.getTargetLockingContextId());
                threads.add(source.getThreadName());
                threads.add(target.getThreadName());
                methods.add(source.getMethodWithClass());
                methods.add(target.getMethodWithClass());
            }
        }
        System.out.println();
        System.out.println("Threads involved in cycles:");
        for (String thread : threads) {
            System.out.println("   " + thread);
        }
        System.out.println();
        System.out.println("Methods involved in cycles:");
        for (String method : methods) {
            System.out.println("   " + method);
        }
        System.out.println();
    }


    private void printInitiallyLoadedStatistics(Iterable<LockNode> locks) {
        int numberOfNodes = 0;
        int numberOfUniqueEdges = 0;
        int numberOfDuplicatedEdges = 0;
        for (LockNode lock : locks) {
            numberOfNodes++;
            numberOfUniqueEdges += lock.numberOfUniqueEdges();
            numberOfDuplicatedEdges += lock.numberOfDuplicatedEdges();
        }
        System.out.println("\nLoaded from database files:");
        System.out.println("   Nodes: " + numberOfNodes);
        System.out.println("   Edges: " + numberOfUniqueEdges
                           + " (excluding " + numberOfDuplicatedEdges
                           + " duplicated)");
    }

    private void createGraphvizFile(String s, int index) throws IOException {
        File file = new File("jcarder_result_" + index + ".gv");
        System.out.println("Writing Graphviz file: " + file.getAbsolutePath());
        FileWriter fw = new FileWriter(file);
        fw.write(s);
        fw.flush();
        fw.close();
    }
}