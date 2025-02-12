package adb.project;

import java.io.*;
import java.util.*;

public class TransactionManager {
    int tick;
    DataManager dm;
    Map<String, Transaction> transactionMap;
    List<String> transactionList;
    List<Operation> operationQ;
    // DeadLock deadLock;
    BufferedReader reader;
    boolean readFromQ; // assign true if last operation was 'E', assign false when read from file
    Integer indexInQ; // not NULL only when readFromQ, used to keep track of which op to remov

    TransactionManager(String inputFileString) {
        tick = 0;
        dm = new DataManager();
        transactionMap = new HashMap<String, Transaction>();
        transactionList = new LinkedList<String>();
        operationQ = new ArrayList<Operation>();
        try {
            reader = new BufferedReader(new FileReader(inputFileString));
        } catch (FileNotFoundException fe) {
            System.out.println("Exception thrown:" + fe);
        }
        // deadlock = new DeadLock();
    }

    void process(Operation op) {
        // check if it can be executed
        System.out.println("Inside process " + Character.toString(op.getType()));
        switch (op.getType()) {
        case 'B':
            Transaction transactionB = new Transaction(op.getTransactionId(), op.getTimeStamp());
            transactionMap.put(op.getTransactionId(), transactionB);
            transactionList.add(op.getTransactionId());
            dm.beginTransactionRW(op.getTransactionId());
            break;
        case 'E':
            Transaction transactionE = transactionMap.get(op.getTransactionId());
            if (dm.canCommit(op.getTransactionId(), transactionE.getBeginTime())) {
                dm.commit(op.getTransactionId(), transactionE.getModifiedVals());
                System.out.println(op.getTransactionId() + " commits");
            } else {
                dm.abort(op.getTransactionId());
                System.out.println(op.getTransactionId() + " aborts");
            }
            break;
        case 'F':
            dm.fail(op.getSiteId(), op.getTimeStamp());
            break;
        case 'H':
            dm.recover(op.getSiteId(), op.getTimeStamp());
            break;
        case 'R':
            ReadLockResponse readResponse = dm.readVal(op.getVar(), op.getTransactionId());
            Transaction currTransaction = transactionMap.get(op.getTransactionId());
            if (readResponse.isGranted()) {
                int ans = readResponse.getVal();
                if (currTransaction.hasModifiedVal(op.getVar()))
                    ans = currTransaction.getModifiedVal(op.getVar());
                System.out.println("x" + Integer.toString(op.getVar()) + ": " + ans);
            } else if (!readResponse.isUnsafe()) {
                operationQ.add(op);
                // deadlock.addEdge(op.getTransactionId(),readResponse.getGuiltyTransactionId());
            } else {
                // TODO: what to do if can't read because of unsafe?
                // for now, skipping the operation
            }
            break;
        case 'W':
            WriteLockResponse writeResponse = dm.writeVal(op.getVar(), op.getTransactionId(), op.getVal());
            if (writeResponse.isGranted()) {
                transactionMap.get(op.getTransactionId()).addModifiedVal(op.getVar(), op.getVal());
                System.out.println("written value x" + Integer.toString(op.getVar()) + ": " + op.getVal());
            } else if (!writeResponse.isUnsafe()) {
                operationQ.add(op);
                // for (String guiltyTransactionId : writeResponse.getGuiltyTransactionIds()) {
                // deadlock.addEdge(op.getTransactionId(), guiltyTransactionId);
                // }
            } else {
                // TODO: what to do if can't write because of unsafe?
                // for now, skipping the operation
            }
            break;
        default:
            break;
        }
        // TODO
        // if yes, execute and remove from q if readFromQ
        // if no, do nothing if read from q, add to q otherwise
    }

    // get next operation either from queue or from file
    Operation getNextOperation() {
        // read from Q if applicable, otherwise call readNextEvent
        if (readFromQ) {
            if (indexInQ == null)
                indexInQ = 0;
            else
                indexInQ++;
            if (indexInQ >= operationQ.size()) {
                readFromQ = false;
                return getNextOperation();
            } else {
                return operationQ.get(indexInQ);
            }
        } else {
            return readNextEvent();
        }
    }

    // read from file
    Operation readNextEvent() {
        String line = readNextLine();
        if (line == null || line.equals(""))
            return null;
        String[] op = line.split("[()]");
        if (op.length < 1)
            return null;
        if (op[0].equals("begin")) {
            return new OperationBE('B', tick++, op[1], false);
        } else if (op[0].equals("beginRO")) {
            return new OperationBE('B', tick++, op[1], false);
        } else if (op[0].equals("end")) {
            return new OperationBE('E', tick++, op[1], false);
        } else if (op[0].equals("R")) {
            String[] splits = op[1].split(",");
            return new OperationRW('R', tick++, splits[0], Integer.parseInt(splits[1].substring(1)), 0);
        } else if (op[0].equals("W")) {
            String[] splits = op[1].split(",");
            return new OperationRW('W', tick++, splits[0], Integer.parseInt(splits[1].substring(1)),
                    Integer.parseInt(splits[2]));
        } else if (op[0].equals("fail")) {
            return new OperationFH('F', tick++, Integer.parseInt(op[1]));
        } else if (op[0].equals("recover")) {
            return new OperationFH('H', tick++, Integer.parseInt(op[1]));
        } else if (op[0].equals("dump")) {
            String dumpOutput = dm.dumpValues();
            System.out.println(dumpOutput);
            tick++;
            return readNextEvent();
        }
        return null;
    }

    // get next line from file
    String readNextLine() {
        String line = null;
        try {
            line = this.reader.readLine();
        } catch (Exception e) {
            System.out.println("Exception thrown:" + e);
        }
        return line;
    }

    // this will be called from main in a loop
    boolean processNextOperation() {
        Operation op = getNextOperation();
        if (op == null) {
            // System.out.println("operation is null");
            return false;
        }
        process(op);
        return true;
    }
}