# DClass plugin for PyCharm

Language support for `.dc` files — the distributed class definitions used by
Panda3D, Astron and [OtpGo](https://github.com/LittleToonCat/OtpGo).

Built against PyCharm 2026.2.

## What is supported

* **Syntax highlighting** - keywords, built-in types, field keywords, ranges,
  divisors, string and hex literals
* **Structure view** - classes, structs, typedefs and switches, with fields
  nested underneath
* **Error checking** - Shows an error for several DC Parse errors
* **Code folding** - collapse a `dclass` or `struct` body
* **Hover documentation** - what a keyword does and what it
  implies; a type's wire size, value range and encoding
* **Go to declaration** - within a `.dc` file, jump from a
  base class, a struct or typedef used as a parameter type.
* **Go to the Python** — jump from a `dclass` name, a field, or an
  import symbol to the Python implementing it.
