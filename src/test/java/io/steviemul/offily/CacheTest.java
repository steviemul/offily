package io.steviemul.offily;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.Serializable;
import java.util.Date;

import org.junit.jupiter.api.Test;

class CacheTest {

  private static final String NAME = ".unit-tests";

  @Test
  void test_basic_cache() {

    long start = new Date().getTime();

    int capacity = 10000;

    Cache<String, Person> cache = new Cache(capacity, NAME);

    for (int i = 0; i < capacity; i++) {
      String key = "key" + i;
      Person person = new Person("firstName" + i, "surname" + i, i);

      cache.put(key, person);

      Person storedValue = cache.get(key);

      assertEquals(person, storedValue);
    }

    for (int i = capacity; i < (capacity * 2); i++) {
      String key = "key" + i;
      Person person = new Person("firstName" + i, "surname" + i, i);

      cache.put(key, person);

      Person storedValue = cache.get(key);

      assertEquals(person, storedValue);
    }

    cache.clear();

    long end = new Date().getTime();

    System.out.println("Number of ms : " + (end - start));

    cache.close();
  }

  record Person(String firstName, String surname, int age) implements Serializable {
  }
}
