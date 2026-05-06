/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.calculator.osproject.v2;

/**
 *
 * @author hp
 */



import java.util.*;

/**
 * CPU Scheduler – all algorithms validated against
 * Silberschatz, Galvin & Gagne "Operating System Concepts" 10th ed, Chapter 5.
 *
 * Algorithms:
 *   FCFS        – non-preemptive, arrival order                  (slide 5.8)
 *   SJF_NP      – non-preemptive, shortest original burst        (slide 5.11)
 *   SJF_P       – preemptive SRTF, shortest remaining time       (slide 5.14)
 *   PRIORITY_NP – non-preemptive, smallest int = highest pri     (slide 5.19)
 *   PRIORITY_P  – preemptive priority                            (slide 5.18)
 *   RR          – round robin with time quantum                  (slide 5.16)
 *
 * Metrics (slide 5.6):
 *   Turnaround time = completion_time - arrival_time
 *   Waiting time    = turnaround_time - burst_time
 */
public class Scheduler {

    public enum Algo { FCFS, SJF_NP, SJF_P, PRIORITY_NP, PRIORITY_P, RR }

    // ── State ─────────────────────────────────────────────────────────
    private List<Process> processes = new ArrayList<>();

    // Non-preemptive algorithms hold this lock until the process finishes
    private Process locked = null;

    // Preemptive algorithms (SJF_P, PRIORITY_P) track who is currently
    // on the CPU so equal-key ties don't cause spurious context switches.
    // FIX: previously used firstRunTime as tiebreak — that is wrong because
    // a previously-run-but-preempted process also has a finite firstRunTime,
    // so it could unfairly win the tiebreak over a brand-new arrival.
    // The correct rule is: the *currently executing* process keeps the CPU
    // when keys are equal. We track this with `currentlyRunning`.
    private Process currentlyRunning = null;

    // RR state
    private final Queue<Process> rrQueue = new LinkedList<>();
    private Process rrCurrent  = null;
    private int     rrTimeLeft = 0;

    private int time    = 0;
    private int quantum = 2;

    // ── Gantt ─────────────────────────────────────────────────────────
    public static class GanttEntry {
        public final String pid;
        public final int    start, end;
        GanttEntry(String pid, int start, int end) {
            this.pid = pid; this.start = start; this.end = end;
        }
    }

    private final List<GanttEntry> gantt = new ArrayList<>();
    private String lastPid   = null;
    private int    lastStart = 0;

    // ── Public API ────────────────────────────────────────────────────
    public void addProcess(Process p)      { processes.add(p); }
    public List<Process>    getProcesses() { return processes; }
    public List<GanttEntry> getGantt()     { return Collections.unmodifiableList(gantt); }
    public void setQuantum(int q)          { quantum = q; }
    public int  getTime()                  { return time; }

    public void removeProcess(Process p) {
        processes.remove(p);
        rrQueue.remove(p);
        if (locked           == p) locked           = null;
        if (currentlyRunning == p) currentlyRunning = null;
        if (rrCurrent        == p) { rrCurrent = null; rrTimeLeft = 0; }
    }

    public boolean isDone() {
        if (processes.isEmpty()) return false;
        for (Process p : processes)
            if (p.remaining > 0) return false;
        return true;
    }

    public void reset() {
        for (Process p : processes) {
            p.remaining    = p.burst;
            p.completion   = 0;
            p.waiting      = 0;
            p.turnaround   = 0;
            p.firstRunTime = Integer.MAX_VALUE;
            p.updateRemaining(p.burst);
            p.updateMetrics(0, 0);
        }
        rrQueue.clear();
        rrCurrent        = null;
        rrTimeLeft       = 0;
        locked           = null;
        currentlyRunning = null;
        time             = 0;
        gantt.clear();
        lastPid   = null;
        lastStart = 0;
    }

    // ── step() – advance simulation by exactly 1 time unit ───────────
    public Process step(Algo algo) {

        if (isDone()) return null;

        // Processes that have arrived and still need CPU
        List<Process> ready = new ArrayList<>();
        for (Process p : processes)
            if (p.arrival <= time && p.remaining > 0)
                ready.add(p);

        Process selected = null;

        switch (algo) {

            // ──────────────────────────────────────────────────────────
            // FCFS – Non-Preemptive  (slide 5.8)
            // Select earliest-arrival process; run to completion.
            // Tiebreak on arrival → PID string (deterministic).
            //
            // Slide 5.8: P1(b=24) P2(b=3) P3(b=3), all arrive t=0
            //   P1[0-24], P2[24-27], P3[27-30], avg WT = 17 ✓
            // ──────────────────────────────────────────────────────────
            case FCFS:
                if (locked != null && locked.remaining == 0) locked = null;
                if (locked == null && !ready.isEmpty())
                    locked = Collections.min(ready,
                        Comparator.comparingInt((Process p) -> p.arrival)
                                  .thenComparing(p -> p.pid));
                selected = locked;
                break;

            // ──────────────────────────────────────────────────────────
            // SJF – Non-Preemptive  (slide 5.11)
            // At dispatch: pick process with shortest ORIGINAL burst.
            // Runs to completion. Tiebreak: arrival, then PID.
            //
            // Slide 5.11: P1(arr=0,b=6) P2(arr=2,b=8)
            //             P3(arr=4,b=7) P4(arr=5,b=3)
            //   t=0 only P1 ready → locked=P1 [0-6]
            //   t=6 ready={P2,P3,P4}; min burst=P4(3) → [6-9]
            //   t=9 ready={P2,P3};    min burst=P1→done, P3(7) < P2(8) → [9-16]
            //   t=16 only P2 → [16-24]
            //   avg WT = (0+16+9+3)/4 = 7 ✓
            //   (Note: slide 5.11 shows all arriving at t=0 for its specific
            //    example giving P4[0-3],P1[3-9],P3[9-16],P2[16-24])
            // ──────────────────────────────────────────────────────────
            case SJF_NP:
                if (locked != null && locked.remaining == 0) locked = null;
                if (locked == null && !ready.isEmpty())
                    locked = Collections.min(ready,
                        Comparator.comparingInt((Process p) -> p.burst)
                                  .thenComparingInt(p -> p.arrival)
                                  .thenComparing(p -> p.pid));
                selected = locked;
                break;

            // ──────────────────────────────────────────────────────────
            // SJF – Preemptive / SRTF  (slide 5.14)
            // Every tick: select ready process with smallest REMAINING time.
            // A newly arrived process with shorter remaining immediately
            // preempts the running one.
            //
            // Tiebreak rule (textbook):
            //   When two processes tie on remaining time, the one already
            //   on the CPU keeps it — no unnecessary context switch.
            //   We implement this by checking `currentlyRunning` first.
            //   Secondary tiebreak: earliest arrival, then PID.
            //
            // Slide 5.14 verification:
            //   P1(arr=0,b=8) P2(arr=1,b=4) P3(arr=2,b=9) P4(arr=3,b=5)
            //   t=0:  {P1(rem=8)}               → P1 [0-1]
            //   t=1:  {P1(7),P2(4)}             → P2 preempts [1-5]
            //   t=2:  P3 arrives (rem=9>P2's 3) → P2 continues
            //   t=3:  P4 arrives (rem=5>P2's 2) → P2 continues
            //   t=5:  P2 done. {P1(6),P3(9),P4(5)} → P4 [5-10]
            //   t=10: {P1(6),P3(9)}             → P1 [10-17]
            //   t=17: {P3(9)}                   → P3 [17-26]
            //   avg WT = [(10-1)+(1-1)+(17-2)+(5-3)]/4 = 6.5 ✓
            // ──────────────────────────────────────────────────────────
            case SJF_P: {
                // Drop stale reference if the process just finished
                if (currentlyRunning != null && currentlyRunning.remaining == 0)
                    currentlyRunning = null;

                if (!ready.isEmpty()) {
                    int minRem = Integer.MAX_VALUE;
                    for (Process p : ready)
                        if (p.remaining < minRem) minRem = p.remaining;

                    final int minRemFinal = minRem;

                    // If the currently running process ties, it keeps the CPU
                    if (currentlyRunning != null
                            && currentlyRunning.remaining == minRemFinal
                            && ready.contains(currentlyRunning)) {
                        selected = currentlyRunning;
                    } else {
                        // Among tied candidates pick earliest arrival, then PID
                        selected = null;
                        for (Process p : ready) {
                            if (p.remaining != minRemFinal) continue;
                            if (selected == null) { selected = p; continue; }
                            if (p.arrival < selected.arrival) { selected = p; continue; }
                            if (p.arrival == selected.arrival
                                    && p.pid.compareTo(selected.pid) < 0)
                                selected = p;
                        }
                    }
                }
                currentlyRunning = selected;
                break;
            }

            // ──────────────────────────────────────────────────────────
            // Priority – Non-Preemptive  (slide 5.19)
            // Smaller integer = higher priority (slide 5.18).
            // At dispatch: pick highest-priority ready process, run to
            // completion.  Tiebreak: arrival, then PID.
            //
            // Slide 5.19 verification (all arrive t=0):
            //   P1(b=10,pri=3) P2(b=1,pri=1) P3(b=2,pri=4)
            //   P4(b=1,pri=5)  P5(b=5,pri=2)
            //   P2[0-1], P5[1-6], P1[6-16], P3[16-18], P4[18-19]
            //   avg WT = (6+0+16+18+1)/5 = 8.2 ✓
            // ──────────────────────────────────────────────────────────
            case PRIORITY_NP:
                if (locked != null && locked.remaining == 0) locked = null;
                if (locked == null && !ready.isEmpty())
                    locked = Collections.min(ready,
                        Comparator.comparingInt((Process p) -> p.priority)
                                  .thenComparingInt(p -> p.arrival)
                                  .thenComparing(p -> p.pid));
                selected = locked;
                break;

            // ──────────────────────────────────────────────────────────
            // Priority – Preemptive  (slide 5.18)
            // Every tick: pick ready process with smallest priority number.
            // A newly arrived higher-priority process immediately preempts.
            //
            // Tiebreak: same pattern as SJF_P — currently running process
            // keeps the CPU when priority values are equal, preventing
            // unnecessary context switches among same-priority processes.
            // ──────────────────────────────────────────────────────────
            case PRIORITY_P: {
                if (currentlyRunning != null && currentlyRunning.remaining == 0)
                    currentlyRunning = null;

                if (!ready.isEmpty()) {
                    int minPri = Integer.MAX_VALUE;
                    for (Process p : ready)
                        if (p.priority < minPri) minPri = p.priority;

                    final int minPriFinal = minPri;

                    if (currentlyRunning != null
                            && currentlyRunning.priority == minPriFinal
                            && ready.contains(currentlyRunning)) {
                        selected = currentlyRunning;
                    } else {
                        selected = null;
                        for (Process p : ready) {
                            if (p.priority != minPriFinal) continue;
                            if (selected == null) { selected = p; continue; }
                            if (p.arrival < selected.arrival) { selected = p; continue; }
                            if (p.arrival == selected.arrival
                                    && p.pid.compareTo(selected.pid) < 0)
                                selected = p;
                        }
                    }
                }
                currentlyRunning = selected;
                break;
            }

            // ──────────────────────────────────────────────────────────
            // Round Robin  (slides 5.15 – 5.16)
            // Each process gets at most `quantum` consecutive ticks, then
            // goes to the back of the ready queue.
            //
            // Queue discipline on every switch:
            //   1. Enqueue newly arrived processes (not already queued,
            //      not the process currently on CPU).
            //   2. Re-enqueue the expiring current process AFTER new
            //      arrivals (standard RR: expiring goes to the back).
            //   3. Poll next process from the front.
            //
            // Slide 5.16 verification (q=4, all arrive t=0):
            //   P1(b=24) P2(b=3) P3(b=3)
            //   P1[0-4], P2[4-7], P3[7-10],
            //   P1[10-14],[14-18],[18-22],[22-26],[26-30]
            //   avg WT = (6+4+7)/3 = 5.67 ✓
            // ──────────────────────────────────────────────────────────
            case RR: {
                boolean finished   = rrCurrent != null && rrCurrent.remaining == 0;
                boolean sliceUp    = rrTimeLeft == 0;
                boolean noCurrent  = rrCurrent == null;
                boolean needSwitch = noCurrent || finished || sliceUp;

                if (needSwitch) {
                    // Step 1: enqueue new arrivals
                    for (Process p : ready)
                        if (!rrQueue.contains(p) && p != rrCurrent)
                            rrQueue.offer(p);

                    // Step 2: re-enqueue expiring (but unfinished) process
                    if (rrCurrent != null && rrCurrent.remaining > 0)
                        rrQueue.offer(rrCurrent);

                    // Step 3: pick next
                    rrCurrent  = rrQueue.poll();
                    rrTimeLeft = quantum;
                } else {
                    // Mid-quantum: still enqueue new arrivals for later
                    for (Process p : ready)
                        if (!rrQueue.contains(p) && p != rrCurrent)
                            rrQueue.offer(p);
                }

                selected = rrCurrent;
                if (selected != null) rrTimeLeft--;
                break;
            }
        }

        // ── Execute one tick ──────────────────────────────────────────
        if (selected != null) {
            if (selected.firstRunTime == Integer.MAX_VALUE)
                selected.firstRunTime = time;

            selected.updateRemaining(selected.remaining - 1);

            if (selected.remaining == 0) {
                // Metrics per slide 5.6
                selected.completion = time + 1;
                int ta = selected.completion - selected.arrival;
                int wt = Math.max(ta - selected.burst, 0);
                selected.updateMetrics(wt, ta);
                if (locked           == selected) locked           = null;
                if (currentlyRunning == selected) currentlyRunning = null;
            }
        }

        // ── Update Gantt ──────────────────────────────────────────────
        String curPid = (selected == null) ? "idle" : selected.pid;
        if (!curPid.equals(lastPid)) {
            if (lastPid != null && time > lastStart)
                gantt.add(new GanttEntry(lastPid, lastStart, time));
            lastPid   = curPid;
            lastStart = time;
        }

        time++;

        // Close final Gantt segment once all processes are done
        if (isDone() && lastPid != null) {
            gantt.add(new GanttEntry(lastPid, lastStart, time));
            lastPid = null;   // guard against duplicate closure
        }

        return selected;
    }

    // ── Metrics (slide 5.6) ───────────────────────────────────────────
    public double avgWaiting() {
        return processes.stream().mapToInt(p -> p.waiting).average().orElse(0);
    }

    public double avgTurnaround() {
        return processes.stream().mapToInt(p -> p.turnaround).average().orElse(0);
    }
}