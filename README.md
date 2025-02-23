# Offily

A simple memory limited key / value store backed by disk.

## Introduction

In situations where heap usage can be unpredictable, for example when processing large volumes of data or traversing
tree structures
of indeterminate length, ensuring we don't get OutOfMemory errors can be difficult.

This can result in a cycle of bumping heap sizes (-Xmx) but doesn't really solve the problem.

This library aims to help by providing an in-memory store that persists items to disk once it's full.

## Details

The in-memory store is an LRUMap that, once full and items get evicted, those items are persisted to a
disk backed key / value store.

This disk backed key value store is implemented using memory mapped files so read and write speeds are comparable with
accessing the heap directly.

## Usage

The Cache object behaves as a normal map.

```
record Person(String firstName, String surname, int age) implements Serializable {
}

// Create a cache with maxiumum 5 in memory objects with name ".people-cache"
// Disk entries will be saved in a folder ".cache/.people-cache
Cache<String, Person> peopleCache = new Cache<>(5, ".people-cache");

store.put("bob", new Person("Bob", "Test", 30));

Person bob = store.get("bob");

assertEquals("Bob", bob.firstName());

peopleCache.close();
```