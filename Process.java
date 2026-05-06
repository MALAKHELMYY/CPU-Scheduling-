/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.calculator.osproject.v2;

/**
 *
 * @author hp
 */

import javafx.beans.property.*;

public class Process {

    public String pid;
    public int    arrival;
    public int    burst;
    public int    priority;
    public int    remaining;
    public int    completion   = 0;
    public int    waiting      = 0;
    public int    turnaround   = 0;
    public int    colorIndex   = 0;
    public int    firstRunTime = Integer.MAX_VALUE;

    // ── Observable properties (TableView binds to these) ──────────────
    private final StringProperty  pidProp;
    private final IntegerProperty arrivalProp;
    private final IntegerProperty burstProp;
    private final IntegerProperty priorityProp;
    private final IntegerProperty remainingProp;
    private final IntegerProperty waitingProp;
    private final IntegerProperty turnaroundProp;

    public Process(String id, int a, int b, int p) {
        pid       = id;
        arrival   = a;
        burst     = b;
        remaining = b;
        priority  = p;

        pidProp        = new SimpleStringProperty(id);
        arrivalProp    = new SimpleIntegerProperty(a);
        burstProp      = new SimpleIntegerProperty(b);
        priorityProp   = new SimpleIntegerProperty(p);
        remainingProp  = new SimpleIntegerProperty(b);
        waitingProp    = new SimpleIntegerProperty(0);
        turnaroundProp = new SimpleIntegerProperty(0);
    }

    // ── Mutators that keep both plain field and property in sync ───────
    public void updateRemaining(int r) {
        remaining = r;
        remainingProp.set(r);
    }

    public void updateMetrics(int w, int t) {
        waiting    = w;
        turnaround = t;
        waitingProp.set(w);
        turnaroundProp.set(t);
    }

    // ── Property accessors (required by PropertyValueFactory) ─────────
    public StringProperty  pidProperty()        { return pidProp;        }
    public IntegerProperty arrivalProperty()    { return arrivalProp;    }
    public IntegerProperty burstProperty()      { return burstProp;      }
    public IntegerProperty priorityProperty()   { return priorityProp;   }
    public IntegerProperty remainingProperty()  { return remainingProp;  }
    public IntegerProperty waitingProperty()    { return waitingProp;    }
    public IntegerProperty turnaroundProperty() { return turnaroundProp; }

    // ── Plain getters (fallback for PropertyValueFactory) ─────────────
    public String getPid()        { return pid;        }
    public int    getArrival()    { return arrival;    }
    public int    getBurst()      { return burst;      }
    public int    getPriority()   { return priority;   }
    public int    getRemaining()  { return remaining;  }
    public int    getWaiting()    { return waiting;    }
    public int    getTurnaround() { return turnaround; }
}