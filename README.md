# Delivery System

## Overview

This project is a university assignment developed by **Viglis E.**, **Lampos A.**, and **Maniatis I.** as part of the **Distributed Systems** course at the Athens University of Economics and Business (Department of Informatics) during the Spring Semester 2024–2025.

The goal was to design and implement a distributed **online food delivery platform**, inspired by systems such as eFood, Wolt, and BOX. Our system is built entirely from scratch using **Java**, avoiding external libraries for networking, threading, and synchronization to deepen our understanding of distributed systems concepts.

The project features:

* A **backend** implemented with distributed components (Master, Workers, Reducer) communicating over **TCP sockets** using a custom-built MapReduce-style framework.
* A **frontend Android application**, enabling customers to browse stores, filter based on preferences, place orders, and rate stores.

---

## Features

### 🛠 Manager Functionality (Console Application)

* Add new stores by uploading a JSON description along with a logo.
* Add, remove, or update products and their stock.
* View total sales per food category (e.g., `pizzeria`) and per product type (e.g., `salad`).
* Data is distributed across Worker nodes based on a hash of the store name, ensuring balanced load.

### 🍔 Customer Functionality (Android App)

* Search for nearby stores within a 5 km radius using filters such as:

  * Food category (e.g., pizzeria, burger)
  * Star ratings (1–5)
  * Price ranges (\$, \$\$, \$\$\$) automatically calculated from the average product prices.
* View store details and menus.
* Place orders for multiple products, with order processing and stock updates handled by the distributed system.
* Rate stores after purchase.

### ⚙️ Backend Architecture

* **Master Node**: A multi-threaded Java TCP server responsible for:

  * Receiving requests from Managers and Customers.
  * Distributing store data to Workers based on hashing.
  * Coordinating MapReduce-like queries to filter or aggregate data.
* **Worker Nodes**: Multi-threaded Java processes storing subsets of the overall data. They:

  * Handle storage and updates entirely in-memory.
  * Respond to map and reduce operations as requested by the Master.
* **Reducer Component**: Aggregates intermediate results from Workers to produce final outputs.

All inter-node communication (Master ↔ Workers, Master ↔ Clients) is done over **TCP sockets** using manual protocols we designed. Synchronization is carefully implemented using only Java’s `synchronized`, `wait`, and `notify`, without advanced concurrent libraries.

---

## 🖼 Project Structure

```
.
├── CustomerApp/
│   ├── app/
│   │   └── src/
│   │       ├── main/
│   │       │   ├── java/com/example/customerapp/
│   │       │   │   ├── adapter/
│   │       │   │   ├── manager/
│   │       │   │   ├── model/
│   │       │   │   ├── network/
│   │       │   │   └── ui/
│   │       │   ├── res/
│   │       └── test/... & androidTest/...
│   ├── build.gradle.kts & gradle settings...
│
└── UpdatedBackend/
    ├── Master/
    ├── Worker/
    ├── Reducer/
    ├── Manager/
    ├── Customer/
    ├── Objects/
    └── data/
        ├── images/
        └── stores/
```

---

## 🔥 Highlights of our work

* Designed a full **MapReduce-like** data query system for searching stores based on customer filters, fully distributed across multiple Workers.
* Implemented robust multi-threaded TCP communication, with synchronized stock updates to prevent race conditions during concurrent purchases.
* Developed a simple yet functional **Android app** that connects directly to the Java Master server using TCP sockets (not HTTP), maintaining open connections with asynchronous communication to keep the UI responsive.

---

## 🚀 How to Run

### Backend

1. **Start the Master** specifying the number of Workers (via arguments or a config file).
2. **Launch Worker nodes**, which dynamically register with the Master.
3. **Run the Manager console app** to add stores and manage products.

### Frontend

* Build and run the Android app using Android Studio.
* The app connects via TCP to the Master to perform searches and purchases.

---

## 📚 Technologies Used

* **Java 8** for all backend components (Master, Worker, Reducer, Manager).
* **Android SDK & Android Studio** for the mobile app.
* Manual handling of:

  * **TCP sockets** for all networking.
  * **Thread synchronization** with `synchronized`, `wait`, `notify`.
  * **In-memory data structures**, no external database used.

---

## 👥 Team

* **Viglis Evangelos**
* **Lampos Andreas**
* **Maniatis Ioannis**

---

✅ **This project successfully fulfills the goals of the Distributed Systems course by demonstrating practical use of:**

* Network programming (TCP sockets).
* Concurrency and synchronization.
* Distributed data management and basic MapReduce paradigms.
* Integration with a frontend client application.

---

🎉 Thank you for exploring our project!
