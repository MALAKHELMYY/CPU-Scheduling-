
# CPU Scheduler Simulator
## About

This project simulates how an operating system schedules CPU processes. Built as a fully interactive JavaFX desktop app, it runs each algorithm live — 1 second per time unit — and visualizes execution through a color-coded Gantt chart that updates in real time.

---

## Supported Algorithms

| Algorithm | Type |
|-----------|------|
| FCFS (First Come First Served) | Non-Preemptive |
| SJF (Shortest Job First) | Non-Preemptive |
| SRTF (Shortest Remaining Time First) | Preemptive |
| Priority Scheduling | Non-Preemptive |
| Priority Scheduling | Preemptive |
| Round Robin | Preemptive |

---

## Features

- 🟢 **Live simulation** — watch processes execute tick by tick in real time
- 📊 **Live Gantt chart** — color-coded per process, drawn as the scheduler runs
- ➕ **Dynamic process addition** — add new processes while the scheduler is running
- 📋 **Live burst time table** — remaining time updates every tick
- 📈 **Performance metrics** — average waiting time and turnaround time on completion
- ⚡ **Static mode** — instantly simulate without the live animation

---

## Tech Stack

- **Java 11**
- **JavaFX 13** — GUI and real-time animations
- **Apache Maven** — build and dependency management
- **OOP Design** — clean separation between scheduler logic, process model, and UI

---

## What I Learned

- **Implementing and comparing multiple scheduling algorithms from scratch
- **Building a real-time animated UI with JavaFX and the Timeline API
- **Syncing live data between backend logic and observable UI properties
- **Designing a clean architecture that separates concerns between the scheduler engine and the GUI
