package org.openrewrite.steve;

import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class SteveRecipeTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
            .recipe(new SteveRecipe3())
            .parser(JavaParser.fromJavaVersion().dependsOn(
                //language=java
                """
                  package x.y.z;
                  public class Constants {
                      public static String SOME_STRING = "some_string";
                      public static String SOME_BOOLEAN = "true";
                      public static String SOME_NUMBER = "1";
                      public static String SOME_DECIMAL_NUMBER = "1.0";
                      public static String SOME_CHAR = "c";
                      public static String SOME_MULTIPLE_STRING = "some_string,another_string";
                      public class Nested {
                          public static String INNER_CHAR = "d";
                      }
                  }
                  """,
                //language=java
                """
                  package x.y.z;
                  public @interface Property {
                    String propertyName;
                    String defaultValue;
                  }
                  """,
                //language=java
                """
                  package x.y.z;
                  import java.lang.annotation.*;
                  @Target(ElementType.ANNOTATION_TYPE)
                  @Retention(RetentionPolicy.RUNTIME)
                  public @interface DefaultValue {
                      @DefaultValue
                      @Target({ ElementType.METHOD, ElementType.FIELD })
                      @Retention(RetentionPolicy.RUNTIME)
                      public @interface String {
                          java.lang.String value();
                      }
                      @DefaultValue
                      @Target({ ElementType.METHOD, ElementType.FIELD })
                      @Retention(RetentionPolicy.RUNTIME)
                      public @interface Class {
                          java.lang.Class<?> value();
                      }
                      @DefaultValue
                      @Target({ ElementType.METHOD, ElementType.FIELD })
                      @Retention(RetentionPolicy.RUNTIME)
                      public @interface Boolean {
                          boolean value();
                      }
                      @DefaultValue
                      @Target({ ElementType.METHOD, ElementType.FIELD })
                      @Retention(RetentionPolicy.RUNTIME)
                      public @interface DefaultByte {
                          byte value();
                      }
                      @DefaultValue
                      @Target({ ElementType.METHOD, ElementType.FIELD })
                      @Retention(RetentionPolicy.RUNTIME)
                      public @interface DefaultChar {
                          char value();
                      }
                      @DefaultValue
                      @Target({ ElementType.METHOD, ElementType.FIELD })
                      @Retention(RetentionPolicy.RUNTIME)
                      public @interface DefaultShort {
                          short value();
                      }
                      @DefaultValue
                      @Target({ ElementType.METHOD, ElementType.FIELD })
                      @Retention(RetentionPolicy.RUNTIME)
                      public @interface Int {
                          int value();
                      }
                      @DefaultValue
                      @Target({ ElementType.METHOD, ElementType.FIELD })
                      @Retention(RetentionPolicy.RUNTIME)
                      public @interface Long {
                          long value();
                      }
                      @DefaultValue
                      @Target({ ElementType.METHOD, ElementType.FIELD })
                      @Retention(RetentionPolicy.RUNTIME)
                      public @interface Float {
                          float value();
                      }
                      @DefaultValue
                      @Target({ ElementType.METHOD, ElementType.FIELD })
                      @Retention(RetentionPolicy.RUNTIME)
                      public @interface Double {
                          double value();
                      }
                      @DefaultValue
                      @Target({ ElementType.METHOD, ElementType.FIELD })
                      @Retention(RetentionPolicy.RUNTIME)
                      public @interface List {
                          java.lang.String[] value();
                      }
                      @DefaultValue
                      @Target({ ElementType.METHOD, ElementType.FIELD })
                      @Retention(RetentionPolicy.RUNTIME)
                      public @interface Array {
                          java.lang.String[] value();
                      }
                      @DefaultValue
                      @Target({ ElementType.METHOD, ElementType.FIELD })
                      @Retention(RetentionPolicy.RUNTIME)
                      public @interface Enum {
                          java.lang.String value();
                      }
                      @DefaultValue
                      @Target({ ElementType.METHOD, ElementType.FIELD })
                      @Retention(RetentionPolicy.RUNTIME)
                      public @interface Set {
                          java.lang.String[] value();
                      }
                  }
                  """
            ));
    }

    @Test
    void convertsPropertyCorrectlyWhenDealingWithMultipleFiles() {
        rewriteRun(
          //language=java
          java(
            """
              import x.y.z.Constants;
              import x.y.z.Property;

              class B {
                  private String someField = "true";

                  @Property(propertyName = "c", defaultValue = someField)
                  public boolean c() {
                      return false;
                  }

                  @Property(propertyName = "d", defaultValue = Constants.SOME_BOOLEAN)
                  public boolean d() {
                      return false;
                  }
              }
              """,
            """
              import x.y.z.Constants;
              import x.y.z.DefaultValue;
              import x.y.z.Property;

              class B {
                  private String someField = "true";
                  private boolean constants__some_booleanAsBoolean = Boolean.parseBoolean(Constants.SOME_BOOLEAN);
                  private boolean someFieldAsBoolean = Boolean.parseBoolean(someField);

                  @DefaultValue.Boolean(someFieldAsBoolean)
                  @Property(propertyName = "c")
                  public boolean c() {
                      return false;
                  }

                  @DefaultValue.Boolean(constants__some_booleanAsBoolean)
                  @Property(propertyName = "d")
                  public boolean d() {
                      return false;
                  }
              }
              """
          ),
          //language=java
          java(
            """
              import x.y.z.Constants;
              import x.y.z.Property;

              class A {
                  private String someField = "false";

                  @Property(propertyName = "a", defaultValue = someField)
                  public boolean a() {
                      return true;
                  }

                  @Property(propertyName = "b", defaultValue = Constants.SOME_BOOLEAN)
                  public boolean b() {
                      return true;
                  }
              }
              """,
            """
              import x.y.z.Constants;
              import x.y.z.DefaultValue;
              import x.y.z.Property;

              class A {
                  private String someField = "false";
                  private boolean constants__some_booleanAsBoolean = Boolean.parseBoolean(Constants.SOME_BOOLEAN);
                  private boolean someFieldAsBoolean = Boolean.parseBoolean(someField);

                  @DefaultValue.Boolean(someFieldAsBoolean)
                  @Property(propertyName = "a")
                  public boolean a() {
                      return true;
                  }

                  @DefaultValue.Boolean(constants__some_booleanAsBoolean)
                  @Property(propertyName = "b")
                  public boolean b() {
                      return true;
                  }
              }
              """
          )
        );
    }

    @Test
    void convertsPropertyForString() {
        rewriteRun(
          //language=java
            java(
              """
                import x.y.z.Constants;
                import x.y.z.Property;

                class A {
                    private String someField = "some_field";

                    @Property(propertyName = "a", defaultValue = "a")
                    public String a() {
                        return "a1";
                    }

                    @Property(propertyName = "b", defaultValue = someField)
                    public String b() {
                        return "a2";
                    }

                    @Property(propertyName = "c", defaultValue = Constants.SOME_STRING)
                    public String c() {
                        return "a3";
                    }

                    @Property(propertyName = "d", defaultValue = B.anotherField)
                    public String d() {
                        return "a4";
                    }

                    class B {
                        public static final String anotherField = "another_field";

                        @Property(propertyName = "e", defaultValue = someField)
                        public String e() {
                            return "a5";
                        }

                        @Property(propertyName = "f", defaultValue = anotherField)
                        public String f() {
                            return "a6";
                        }

                        @Property(propertyName = "g", defaultValue = Constants.SOME_STRING)
                        public String g() {
                            return "a7";
                        }
                    }
                }
                """,
              """
                import x.y.z.Constants;
                import x.y.z.DefaultValue;
                import x.y.z.Property;

                class A {
                    private String someField = "some_field";

                    @DefaultValue.String("a")
                    @Property(propertyName = "a")
                    public String a() {
                        return "a1";
                    }

                    @DefaultValue.String(someField)
                    @Property(propertyName = "b")
                    public String b() {
                        return "a2";
                    }

                    @DefaultValue.String(Constants.SOME_STRING)
                    @Property(propertyName = "c")
                    public String c() {
                        return "a3";
                    }

                    @DefaultValue.String(B.anotherField)
                    @Property(propertyName = "d")
                    public String d() {
                        return "a4";
                    }

                    class B {
                        public static final String anotherField = "another_field";

                        @DefaultValue.String(someField)
                        @Property(propertyName = "e")
                        public String e() {
                            return "a5";
                        }

                        @DefaultValue.String(anotherField)
                        @Property(propertyName = "f")
                        public String f() {
                            return "a6";
                        }

                        @DefaultValue.String(Constants.SOME_STRING)
                        @Property(propertyName = "g")
                        public String g() {
                            return "a7";
                        }
                    }
                }
                """
            )
        );
    }

    @Test
    void convertsPropertyForBoolean() {
        rewriteRun(
          //language=java
          java(
            """
              import x.y.z.Constants;
              import x.y.z.Property;

              class A {
                  private String someField = "false";

                  @Property(propertyName = "a", defaultValue = "true")
                  public Boolean a() {
                      return true;
                  }

                  @Property(propertyName = "b", defaultValue = "false")
                  public boolean b() {
                      return false;
                  }

                  @Property(propertyName = "c", defaultValue = someField)
                  public boolean c() {
                      return true;
                  }

                  @Property(propertyName = "d", defaultValue = Constants.SOME_BOOLEAN)
                  public boolean d() {
                      return true;
                  }

                  @Property(propertyName = "e", defaultValue = B.anotherField)
                  public boolean e() {
                      return true;
                  }

                  class B {
                      public static final String anotherField = "true";
                      public static final String aThirdField = "false";

                      @Property(propertyName = "f", defaultValue = someField)
                      public boolean f() {
                          return true;
                      }

                      @Property(propertyName = "g", defaultValue = anotherField)
                      public boolean g() {
                          return true;
                      }

                      @Property(propertyName = "h", defaultValue = aThirdField)
                      public boolean h() {
                          return true;
                      }

                      @Property(propertyName = "i", defaultValue = Constants.SOME_BOOLEAN)
                      public boolean i() {
                          return true;
                      }
                  }
              }
              """,
            """
              import x.y.z.Constants;
              import x.y.z.DefaultValue;
              import x.y.z.Property;

              class A {
                  private String someField = "false";
                  private boolean b__anotherfieldAsBoolean = Boolean.parseBoolean(B.anotherField);
                  private boolean constants__some_booleanAsBoolean = Boolean.parseBoolean(Constants.SOME_BOOLEAN);
                  private boolean someFieldAsBoolean = Boolean.parseBoolean(someField);

                  @DefaultValue.Boolean(true)
                  @Property(propertyName = "a")
                  public Boolean a() {
                      return true;
                  }

                  @DefaultValue.Boolean(false)
                  @Property(propertyName = "b")
                  public boolean b() {
                      return false;
                  }

                  @DefaultValue.Boolean(someFieldAsBoolean)
                  @Property(propertyName = "c")
                  public boolean c() {
                      return true;
                  }

                  @DefaultValue.Boolean(constants__some_booleanAsBoolean)
                  @Property(propertyName = "d")
                  public boolean d() {
                      return true;
                  }

                  @DefaultValue.Boolean(b__anotherfieldAsBoolean)
                  @Property(propertyName = "e")
                  public boolean e() {
                      return true;
                  }

                  class B {
                      public static final String anotherField = "true";
                      public static final String aThirdField = "false";
                      private boolean aThirdFieldAsBoolean = Boolean.parseBoolean(aThirdField);

                      @DefaultValue.Boolean(someFieldAsBoolean)
                      @Property(propertyName = "f")
                      public boolean f() {
                          return true;
                      }

                      @DefaultValue.Boolean(b__anotherfieldAsBoolean)
                      @Property(propertyName = "g")
                      public boolean g() {
                          return true;
                      }

                      @DefaultValue.Boolean(aThirdFieldAsBoolean)
                      @Property(propertyName = "h")
                      public boolean h() {
                          return true;
                      }

                      @DefaultValue.Boolean(constants__some_booleanAsBoolean)
                      @Property(propertyName = "i")
                      public boolean i() {
                          return true;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void convertsPropertyForByte() {
        rewriteRun(
          //language=java
          java(
            """
              import x.y.z.Constants;
              import x.y.z.Property;

              class A {
                  private String someField = "1";

                  @Property(propertyName = "a", defaultValue = "2")
                  public Byte a() {
                      return 0;
                  }

                  @Property(propertyName = "b", defaultValue = "3")
                  public byte b() {
                      return 0;
                  }

                  @Property(propertyName = "c", defaultValue = someField)
                  public byte c() {
                      return 0;
                  }

                  @Property(propertyName = "d", defaultValue = Constants.SOME_NUMBER)
                  public byte d() {
                      return 0;
                  }

                  @Property(propertyName = "e", defaultValue = B.anotherField)
                  public byte e() {
                      return 0;
                  }

                  class B {
                      public static final String anotherField = "4";
                      public static final String aThirdField = "5";

                      @Property(propertyName = "f", defaultValue = someField)
                      public byte f() {
                          return 0;
                      }

                      @Property(propertyName = "g", defaultValue = anotherField)
                      public byte g() {
                          return 0;
                      }

                      @Property(propertyName = "h", defaultValue = aThirdField)
                      public byte h() {
                          return 0;
                      }

                      @Property(propertyName = "i", defaultValue = Constants.SOME_NUMBER)
                      public byte i() {
                          return 0;
                      }
                  }
              }
              """,
            """
              import x.y.z.Constants;
              import x.y.z.DefaultValue;
              import x.y.z.Property;

              class A {
                  private String someField = "1";
                  private byte b__anotherfieldAsByte = Byte.parseByte(B.anotherField);
                  private byte constants__some_numberAsByte = Byte.parseByte(Constants.SOME_NUMBER);
                  private byte someFieldAsByte = Byte.parseByte(someField);

                  @DefaultValue.DefaultByte(2)
                  @Property(propertyName = "a")
                  public Byte a() {
                      return 0;
                  }

                  @DefaultValue.DefaultByte(3)
                  @Property(propertyName = "b")
                  public byte b() {
                      return 0;
                  }

                  @DefaultValue.DefaultByte(someFieldAsByte)
                  @Property(propertyName = "c")
                  public byte c() {
                      return 0;
                  }

                  @DefaultValue.DefaultByte(constants__some_numberAsByte)
                  @Property(propertyName = "d")
                  public byte d() {
                      return 0;
                  }

                  @DefaultValue.DefaultByte(b__anotherfieldAsByte)
                  @Property(propertyName = "e")
                  public byte e() {
                      return 0;
                  }

                  class B {
                      public static final String anotherField = "4";
                      public static final String aThirdField = "5";
                      private byte aThirdFieldAsByte = Byte.parseByte(aThirdField);

                      @DefaultValue.DefaultByte(someFieldAsByte)
                      @Property(propertyName = "f")
                      public byte f() {
                          return 0;
                      }

                      @DefaultValue.DefaultByte(b__anotherfieldAsByte)
                      @Property(propertyName = "g")
                      public byte g() {
                          return 0;
                      }

                      @DefaultValue.DefaultByte(aThirdFieldAsByte)
                      @Property(propertyName = "h")
                      public byte h() {
                          return 0;
                      }

                      @DefaultValue.DefaultByte(constants__some_numberAsByte)
                      @Property(propertyName = "i")
                      public byte i() {
                          return 0;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void convertsPropertyForChar() {
        rewriteRun(
          //language=java
          java(
            """
              import x.y.z.Constants;
              import x.y.z.Property;

              class A {
                  private String someField = "z";

                  @Property(propertyName = "a", defaultValue = "a")
                  public Character a() {
                      return 'x';
                  }

                  @Property(propertyName = "b", defaultValue = "b")
                  public char b() {
                      return 'x';
                  }

                  @Property(propertyName = "c", defaultValue = someField)
                  public char c() {
                      return 'x';
                  }

                  @Property(propertyName = "d", defaultValue = Constants.SOME_CHAR)
                  public char d() {
                      return 'x';
                  }

                  @Property(propertyName = "d2", defaultValue = Constants.Nested.INNER_CHAR)
                  public char d2() {
                      return 'x';
                  }

                  @Property(propertyName = "e", defaultValue = B.anotherField)
                  public char e() {
                      return 'x';
                  }

                  class B {
                      public static final String anotherField = "y";
                      public static final String aThirdField = "w";

                      @Property(propertyName = "f", defaultValue = someField)
                      public char f() {
                          return 'x';
                      }

                      @Property(propertyName = "g", defaultValue = anotherField)
                      public char g() {
                          return 'x';
                      }

                      @Property(propertyName = "h", defaultValue = aThirdField)
                      public char h() {
                          return 'x';
                      }

                      @Property(propertyName = "i", defaultValue = Constants.SOME_CHAR)
                      public char i() {
                          return 'x';
                      }

                      @Property(propertyName = "j", defaultValue = C.aFourthField)
                      public char j() {
                          return 'x';
                      }
                  }

                  class C {
                      public static final String aFourthField = "u";
                  }
              }
              """,
            """
              import x.y.z.Constants;
              import x.y.z.DefaultValue;
              import x.y.z.Property;

              class A {
                  private String someField = "z";
                  private char b__anotherfieldAsChar = B.anotherField.charAt(0);
                  private char constants__nested__inner_charAsChar = Constants.Nested.INNER_CHAR.charAt(0);
                  private char constants__some_charAsChar = Constants.SOME_CHAR.charAt(0);
                  private char someFieldAsChar = someField.charAt(0);

                  @DefaultValue.DefaultChar('a')
                  @Property(propertyName = "a")
                  public Character a() {
                      return 'x';
                  }

                  @DefaultValue.DefaultChar('b')
                  @Property(propertyName = "b")
                  public char b() {
                      return 'x';
                  }

                  @DefaultValue.DefaultChar(someFieldAsChar)
                  @Property(propertyName = "c")
                  public char c() {
                      return 'x';
                  }

                  @DefaultValue.DefaultChar(constants__some_charAsChar)
                  @Property(propertyName = "d")
                  public char d() {
                      return 'x';
                  }

                  @DefaultValue.DefaultChar(constants__nested__inner_charAsChar)
                  @Property(propertyName = "d2")
                  public char d2() {
                      return 'x';
                  }

                  @DefaultValue.DefaultChar(b__anotherfieldAsChar)
                  @Property(propertyName = "e")
                  public char e() {
                      return 'x';
                  }

                  class B {
                      public static final String anotherField = "y";
                      public static final String aThirdField = "w";
                      private char aThirdFieldAsChar = aThirdField.charAt(0);
                      private char c__afourthfieldAsChar = C.aFourthField.charAt(0);

                      @DefaultValue.DefaultChar(someFieldAsChar)
                      @Property(propertyName = "f")
                      public char f() {
                          return 'x';
                      }

                      @DefaultValue.DefaultChar(b__anotherfieldAsChar)
                      @Property(propertyName = "g")
                      public char g() {
                          return 'x';
                      }

                      @DefaultValue.DefaultChar(aThirdFieldAsChar)
                      @Property(propertyName = "h")
                      public char h() {
                          return 'x';
                      }

                      @DefaultValue.DefaultChar(constants__some_charAsChar)
                      @Property(propertyName = "i")
                      public char i() {
                          return 'x';
                      }

                      @DefaultValue.DefaultChar(c__afourthfieldAsChar)
                      @Property(propertyName = "j")
                      public char j() {
                          return 'x';
                      }
                  }

                  class C {
                      public static final String aFourthField = "u";
                  }
              }
              """
          )
        );
    }

    @Test
    void convertsPropertyForShort() {
        rewriteRun(
          //language=java
          java(
            """
              import x.y.z.Constants;
              import x.y.z.Property;

              class A {
                  private String someField = "1";

                  @Property(propertyName = "a", defaultValue = "2")
                  public Short a() {
                      return 0;
                  }

                  @Property(propertyName = "b", defaultValue = "3")
                  public short b() {
                      return 0;
                  }

                  @Property(propertyName = "c", defaultValue = someField)
                  public short c() {
                      return 0;
                  }

                  @Property(propertyName = "d", defaultValue = Constants.SOME_NUMBER)
                  public short d() {
                      return 0;
                  }

                  @Property(propertyName = "e", defaultValue = B.anotherField)
                  public short e() {
                      return 0;
                  }

                  class B {
                      public static final String anotherField = "4";
                      public static final String aThirdField = "5";

                      @Property(propertyName = "f", defaultValue = someField)
                      public short f() {
                          return 0;
                      }

                      @Property(propertyName = "g", defaultValue = anotherField)
                      public short g() {
                          return 0;
                      }

                      @Property(propertyName = "h", defaultValue = aThirdField)
                      public short h() {
                          return 0;
                      }

                      @Property(propertyName = "i", defaultValue = Constants.SOME_NUMBER)
                      public short i() {
                          return 0;
                      }
                  }
              }
              """,
            """
              import x.y.z.Constants;
              import x.y.z.DefaultValue;
              import x.y.z.Property;

              class A {
                  private String someField = "1";
                  private short b__anotherfieldAsShort = Short.parseShort(B.anotherField);
                  private short constants__some_numberAsShort = Short.parseShort(Constants.SOME_NUMBER);
                  private short someFieldAsShort = Short.parseShort(someField);

                  @DefaultValue.DefaultShort(2)
                  @Property(propertyName = "a")
                  public Short a() {
                      return 0;
                  }

                  @DefaultValue.DefaultShort(3)
                  @Property(propertyName = "b")
                  public short b() {
                      return 0;
                  }

                  @DefaultValue.DefaultShort(someFieldAsShort)
                  @Property(propertyName = "c")
                  public short c() {
                      return 0;
                  }

                  @DefaultValue.DefaultShort(constants__some_numberAsShort)
                  @Property(propertyName = "d")
                  public short d() {
                      return 0;
                  }

                  @DefaultValue.DefaultShort(b__anotherfieldAsShort)
                  @Property(propertyName = "e")
                  public short e() {
                      return 0;
                  }

                  class B {
                      public static final String anotherField = "4";
                      public static final String aThirdField = "5";
                      private short aThirdFieldAsShort = Short.parseShort(aThirdField);

                      @DefaultValue.DefaultShort(someFieldAsShort)
                      @Property(propertyName = "f")
                      public short f() {
                          return 0;
                      }

                      @DefaultValue.DefaultShort(b__anotherfieldAsShort)
                      @Property(propertyName = "g")
                      public short g() {
                          return 0;
                      }

                      @DefaultValue.DefaultShort(aThirdFieldAsShort)
                      @Property(propertyName = "h")
                      public short h() {
                          return 0;
                      }

                      @DefaultValue.DefaultShort(constants__some_numberAsShort)
                      @Property(propertyName = "i")
                      public short i() {
                          return 0;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void convertsPropertyForInt() {
        rewriteRun(
          //language=java
          java(
            """
              import x.y.z.Constants;
              import x.y.z.Property;

              class A {
                  private String someField = "1";

                  @Property(propertyName = "a", defaultValue = "2")
                  public Integer a() {
                      return 0;
                  }

                  @Property(propertyName = "b", defaultValue = "3")
                  public int b() {
                      return 0;
                  }

                  @Property(propertyName = "c", defaultValue = someField)
                  public int c() {
                      return 0;
                  }

                  @Property(propertyName = "d", defaultValue = Constants.SOME_NUMBER)
                  public int d() {
                      return 0;
                  }

                  @Property(propertyName = "e", defaultValue = B.anotherField)
                  public int e() {
                      return 0;
                  }

                  class B {
                      public static final String anotherField = "4";
                      public static final String aThirdField = "5";

                      @Property(propertyName = "f", defaultValue = someField)
                      public int f() {
                          return 0;
                      }

                      @Property(propertyName = "g", defaultValue = anotherField)
                      public int g() {
                          return 0;
                      }

                      @Property(propertyName = "h", defaultValue = aThirdField)
                      public int h() {
                          return 0;
                      }

                      @Property(propertyName = "i", defaultValue = Constants.SOME_NUMBER)
                      public int i() {
                          return 0;
                      }
                  }
              }
              """,
            """
              import x.y.z.Constants;
              import x.y.z.DefaultValue;
              import x.y.z.Property;

              class A {
                  private String someField = "1";
                  private int b__anotherfieldAsInt = Integer.parseInt(B.anotherField);
                  private int constants__some_numberAsInt = Integer.parseInt(Constants.SOME_NUMBER);
                  private int someFieldAsInt = Integer.parseInt(someField);

                  @DefaultValue.Int(2)
                  @Property(propertyName = "a")
                  public Integer a() {
                      return 0;
                  }

                  @DefaultValue.Int(3)
                  @Property(propertyName = "b")
                  public int b() {
                      return 0;
                  }

                  @DefaultValue.Int(someFieldAsInt)
                  @Property(propertyName = "c")
                  public int c() {
                      return 0;
                  }

                  @DefaultValue.Int(constants__some_numberAsInt)
                  @Property(propertyName = "d")
                  public int d() {
                      return 0;
                  }

                  @DefaultValue.Int(b__anotherfieldAsInt)
                  @Property(propertyName = "e")
                  public int e() {
                      return 0;
                  }

                  class B {
                      public static final String anotherField = "4";
                      public static final String aThirdField = "5";
                      private int aThirdFieldAsInt = Integer.parseInt(aThirdField);

                      @DefaultValue.Int(someFieldAsInt)
                      @Property(propertyName = "f")
                      public int f() {
                          return 0;
                      }

                      @DefaultValue.Int(b__anotherfieldAsInt)
                      @Property(propertyName = "g")
                      public int g() {
                          return 0;
                      }

                      @DefaultValue.Int(aThirdFieldAsInt)
                      @Property(propertyName = "h")
                      public int h() {
                          return 0;
                      }

                      @DefaultValue.Int(constants__some_numberAsInt)
                      @Property(propertyName = "i")
                      public int i() {
                          return 0;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void convertsPropertyForLong() {
        rewriteRun(
          //language=java
          java(
            """
              import x.y.z.Constants;
              import x.y.z.Property;

              class A {
                  private String someField = "1";

                  @Property(propertyName = "a", defaultValue = "2")
                  public Long a() {
                      return 0;
                  }

                  @Property(propertyName = "b", defaultValue = "3")
                  public long b() {
                      return 0;
                  }

                  @Property(propertyName = "c", defaultValue = someField)
                  public long c() {
                      return 0;
                  }

                  @Property(propertyName = "d", defaultValue = Constants.SOME_NUMBER)
                  public long d() {
                      return 0;
                  }

                  @Property(propertyName = "e", defaultValue = B.anotherField)
                  public long e() {
                      return 0;
                  }

                  class B {
                      public static final String anotherField = "4";
                      public static final String aThirdField = "5";

                      @Property(propertyName = "f", defaultValue = someField)
                      public long f() {
                          return 0;
                      }

                      @Property(propertyName = "g", defaultValue = anotherField)
                      public long g() {
                          return 0;
                      }

                      @Property(propertyName = "h", defaultValue = aThirdField)
                      public long h() {
                          return 0;
                      }

                      @Property(propertyName = "i", defaultValue = Constants.SOME_NUMBER)
                      public long i() {
                          return 0;
                      }
                  }
              }
              """,
            """
              import x.y.z.Constants;
              import x.y.z.DefaultValue;
              import x.y.z.Property;

              class A {
                  private String someField = "1";
                  private long b__anotherfieldAsLong = Long.parseLong(B.anotherField);
                  private long constants__some_numberAsLong = Long.parseLong(Constants.SOME_NUMBER);
                  private long someFieldAsLong = Long.parseLong(someField);

                  @DefaultValue.Long(2)
                  @Property(propertyName = "a")
                  public Long a() {
                      return 0;
                  }

                  @DefaultValue.Long(3)
                  @Property(propertyName = "b")
                  public long b() {
                      return 0;
                  }

                  @DefaultValue.Long(someFieldAsLong)
                  @Property(propertyName = "c")
                  public long c() {
                      return 0;
                  }

                  @DefaultValue.Long(constants__some_numberAsLong)
                  @Property(propertyName = "d")
                  public long d() {
                      return 0;
                  }

                  @DefaultValue.Long(b__anotherfieldAsLong)
                  @Property(propertyName = "e")
                  public long e() {
                      return 0;
                  }

                  class B {
                      public static final String anotherField = "4";
                      public static final String aThirdField = "5";
                      private long aThirdFieldAsLong = Long.parseLong(aThirdField);

                      @DefaultValue.Long(someFieldAsLong)
                      @Property(propertyName = "f")
                      public long f() {
                          return 0;
                      }

                      @DefaultValue.Long(b__anotherfieldAsLong)
                      @Property(propertyName = "g")
                      public long g() {
                          return 0;
                      }

                      @DefaultValue.Long(aThirdFieldAsLong)
                      @Property(propertyName = "h")
                      public long h() {
                          return 0;
                      }

                      @DefaultValue.Long(constants__some_numberAsLong)
                      @Property(propertyName = "i")
                      public long i() {
                          return 0;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void convertsPropertyForFloat() {
        rewriteRun(
          //language=java
          java(
            """
              import x.y.z.Constants;
              import x.y.z.Property;

              class A {
                  private String someField = "1.0";

                  @Property(propertyName = "a", defaultValue = "2.0")
                  public Float a() {
                      return 0.0;
                  }

                  @Property(propertyName = "b", defaultValue = "3.0")
                  public float b() {
                      return 0.0;
                  }

                  @Property(propertyName = "c", defaultValue = someField)
                  public float c() {
                      return 0.0;
                  }

                  @Property(propertyName = "d", defaultValue = Constants.SOME_DECIMAL_NUMBER)
                  public float d() {
                      return 0.0;
                  }

                  @Property(propertyName = "e", defaultValue = B.anotherField)
                  public float e() {
                      return 0.0;
                  }

                  class B {
                      public static final String anotherField = "4.0";
                      public static final String aThirdField = "5.0";

                      @Property(propertyName = "f", defaultValue = someField)
                      public float f() {
                          return 0.0;
                      }

                      @Property(propertyName = "g", defaultValue = anotherField)
                      public float g() {
                          return 0.0;
                      }

                      @Property(propertyName = "h", defaultValue = aThirdField)
                      public float h() {
                          return 0.0;
                      }

                      @Property(propertyName = "i", defaultValue = Constants.SOME_DECIMAL_NUMBER)
                      public float i() {
                          return 0.0;
                      }
                  }
              }
              """,
            """
              import x.y.z.Constants;
              import x.y.z.DefaultValue;
              import x.y.z.Property;

              class A {
                  private String someField = "1.0";
                  private float b__anotherfieldAsFloat = Float.parseFloat(B.anotherField);
                  private float constants__some_decimal_numberAsFloat = Float.parseFloat(Constants.SOME_DECIMAL_NUMBER);
                  private float someFieldAsFloat = Float.parseFloat(someField);

                  @DefaultValue.Float(2.0)
                  @Property(propertyName = "a")
                  public Float a() {
                      return 0.0;
                  }

                  @DefaultValue.Float(3.0)
                  @Property(propertyName = "b")
                  public float b() {
                      return 0.0;
                  }

                  @DefaultValue.Float(someFieldAsFloat)
                  @Property(propertyName = "c")
                  public float c() {
                      return 0.0;
                  }

                  @DefaultValue.Float(constants__some_decimal_numberAsFloat)
                  @Property(propertyName = "d")
                  public float d() {
                      return 0.0;
                  }

                  @DefaultValue.Float(b__anotherfieldAsFloat)
                  @Property(propertyName = "e")
                  public float e() {
                      return 0.0;
                  }

                  class B {
                      public static final String anotherField = "4.0";
                      public static final String aThirdField = "5.0";
                      private float aThirdFieldAsFloat = Float.parseFloat(aThirdField);

                      @DefaultValue.Float(someFieldAsFloat)
                      @Property(propertyName = "f")
                      public float f() {
                          return 0.0;
                      }

                      @DefaultValue.Float(b__anotherfieldAsFloat)
                      @Property(propertyName = "g")
                      public float g() {
                          return 0.0;
                      }

                      @DefaultValue.Float(aThirdFieldAsFloat)
                      @Property(propertyName = "h")
                      public float h() {
                          return 0.0;
                      }

                      @DefaultValue.Float(constants__some_decimal_numberAsFloat)
                      @Property(propertyName = "i")
                      public float i() {
                          return 0.0;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void convertsPropertyForDouble() {
        rewriteRun(
          //language=java
          java(
            """
              import x.y.z.Constants;
              import x.y.z.Property;

              class A {
                  private String someField = "1.0";

                  @Property(propertyName = "a", defaultValue = "2.0")
                  public Double a() {
                      return 0.0;
                  }

                  @Property(propertyName = "b", defaultValue = "3.0")
                  public double b() {
                      return 0.0;
                  }

                  @Property(propertyName = "c", defaultValue = someField)
                  public double c() {
                      return 0.0;
                  }

                  @Property(propertyName = "d", defaultValue = Constants.SOME_DECIMAL_NUMBER)
                  public double d() {
                      return 0.0;
                  }

                  @Property(propertyName = "e", defaultValue = B.anotherField)
                  public double e() {
                      return 0.0;
                  }

                  class B {
                      public static final String anotherField = "4.0";
                      public static final String aThirdField = "5.0";

                      @Property(propertyName = "f", defaultValue = someField)
                      public double f() {
                          return 0.0;
                      }

                      @Property(propertyName = "g", defaultValue = anotherField)
                      public double g() {
                          return 0.0;
                      }

                      @Property(propertyName = "h", defaultValue = aThirdField)
                      public double h() {
                          return 0.0;
                      }

                      @Property(propertyName = "i", defaultValue = Constants.SOME_DECIMAL_NUMBER)
                      public double i() {
                          return 0.0;
                      }
                  }
              }
              """,
            """
              import x.y.z.Constants;
              import x.y.z.DefaultValue;
              import x.y.z.Property;

              class A {
                  private String someField = "1.0";
                  private double b__anotherfieldAsDouble = Double.parseDouble(B.anotherField);
                  private double constants__some_decimal_numberAsDouble = Double.parseDouble(Constants.SOME_DECIMAL_NUMBER);
                  private double someFieldAsDouble = Double.parseDouble(someField);

                  @DefaultValue.Double(2.0)
                  @Property(propertyName = "a")
                  public Double a() {
                      return 0.0;
                  }

                  @DefaultValue.Double(3.0)
                  @Property(propertyName = "b")
                  public double b() {
                      return 0.0;
                  }

                  @DefaultValue.Double(someFieldAsDouble)
                  @Property(propertyName = "c")
                  public double c() {
                      return 0.0;
                  }

                  @DefaultValue.Double(constants__some_decimal_numberAsDouble)
                  @Property(propertyName = "d")
                  public double d() {
                      return 0.0;
                  }

                  @DefaultValue.Double(b__anotherfieldAsDouble)
                  @Property(propertyName = "e")
                  public double e() {
                      return 0.0;
                  }

                  class B {
                      public static final String anotherField = "4.0";
                      public static final String aThirdField = "5.0";
                      private double aThirdFieldAsDouble = Double.parseDouble(aThirdField);

                      @DefaultValue.Double(someFieldAsDouble)
                      @Property(propertyName = "f")
                      public double f() {
                          return 0.0;
                      }

                      @DefaultValue.Double(b__anotherfieldAsDouble)
                      @Property(propertyName = "g")
                      public double g() {
                          return 0.0;
                      }

                      @DefaultValue.Double(aThirdFieldAsDouble)
                      @Property(propertyName = "h")
                      public double h() {
                          return 0.0;
                      }

                      @DefaultValue.Double(constants__some_decimal_numberAsDouble)
                      @Property(propertyName = "i")
                      public double i() {
                          return 0.0;
                      }
                  }
              }
              """
          )
        );
    }

    @Test
    void convertsPropertyForStringListArrayOrSet() {
        rewriteRun(
          //language=java
          java(
            """
              import java.util.*;
              import x.y.z.Constants;
              import x.y.z.Property;

              class A {
                  private String someField = "some_field";
                  private String someMultipleField = someField + ",some_other_field";

                  @Property(propertyName = "aSet", defaultValue = "a,b")
                  public Set<String> aSet() {
                      return new HashSet<>() {{
                          add("aSet");
                      }};
                  }

                  @Property(propertyName = "aArrList", defaultValue = "a,b")
                  public ArrayList<String> aArrList() {
                      return new ArrayList<>(Collections.singletonList("aArrList"));
                  }

                  @Property(propertyName = "aArr", defaultValue = "a,b")
                  public String[] aArr() {
                      return new String[] { "aArr" };
                  }

                  @Property(propertyName = "aList", defaultValue = "a,b")
                  public List<String> aList() {
                      return Collections.singletonList("a1");
                  }

                  @Property(propertyName = "b", defaultValue = "b")
                  public List<String> b() {
                      return Collections.singletonList("a2");
                  }

                  @Property(propertyName = "c", defaultValue = someField)
                  public List<String> c() {
                      return Collections.singletonList("a3");
                  }

                  @Property(propertyName = "d", defaultValue = someMultipleField)
                  public List<String> d() {
                      return Collections.singletonList("a4");
                  }

                  @Property(propertyName = "e", defaultValue = Constants.SOME_STRING)
                  public List<String> e() {
                      return Collections.singletonList("a5");
                  }

                  @Property(propertyName = "f", defaultValue = B.anotherField)
                  public List<String> f() {
                      return Collections.singletonList("a6");
                  }

                  class B {
                      public static final String anotherField = "another_field";
                      public static final String anotherMultipleField = anotherField + ",another_other_field";

                      @Property(propertyName = "g", defaultValue = someField)
                      public List<String> g() {
                          return Collections.singletonList("a7");
                      }

                      @Property(propertyName = "h", defaultValue = someMultipleField)
                      public List<String> h() {
                          return Collections.singletonList("a8");
                      }

                      @Property(propertyName = "i", defaultValue = anotherField)
                      public List<String> i() {
                          return Collections.singletonList("a9");
                      }

                      @Property(propertyName = "j", defaultValue = anotherMultipleField)
                      public List<String> j() {
                          return Collections.singletonList("a10");
                      }

                      @Property(propertyName = "k", defaultValue = Constants.SOME_STRING)
                      public List<String> k() {
                          return Collections.singletonList("a11");
                      }

                      @Property(propertyName = "l", defaultValue = Constants.SOME_MULTIPLE_STRING)
                      public List<String> l() {
                          return Collections.singletonList("a12");
                      }
                  }
              }
              """,
            """
              import java.util.*;
              import x.y.z.Constants;
              import x.y.z.DefaultValue;
              import x.y.z.Property;

              class A {
                  private String someField = "some_field";
                  private String someMultipleField = someField + ",some_other_field";
                  private String[] b__anotherfieldAsArray = B.anotherField.split(",");
                  private String[] constants__some_stringAsArray = Constants.SOME_STRING.split(",");
                  private String[] someFieldAsArray = someField.split(",");
                  private String[] someMultipleFieldAsArray = someMultipleField.split(",");

                  @DefaultValue.Set({"a", "b"})
                  @Property(propertyName = "aSet")
                  public Set<String> aSet() {
                      return new HashSet<>() {{
                          add("aSet");
                      }};
                  }

                  @DefaultValue.List({"a", "b"})
                  @Property(propertyName = "aArrList")
                  public ArrayList<String> aArrList() {
                      return new ArrayList<>(Collections.singletonList("aArrList"));
                  }

                  @DefaultValue.Array({"a", "b"})
                  @Property(propertyName = "aArr")
                  public String[] aArr() {
                      return new String[] { "aArr" };
                  }

                  @DefaultValue.List({"a", "b"})
                  @Property(propertyName = "aList")
                  public List<String> aList() {
                      return Collections.singletonList("a1");
                  }

                  @DefaultValue.List({"b"})
                  @Property(propertyName = "b")
                  public List<String> b() {
                      return Collections.singletonList("a2");
                  }

                  @DefaultValue.List(someFieldAsArray)
                  @Property(propertyName = "c")
                  public List<String> c() {
                      return Collections.singletonList("a3");
                  }

                  @DefaultValue.List(someMultipleFieldAsArray)
                  @Property(propertyName = "d")
                  public List<String> d() {
                      return Collections.singletonList("a4");
                  }

                  @DefaultValue.List(constants__some_stringAsArray)
                  @Property(propertyName = "e")
                  public List<String> e() {
                      return Collections.singletonList("a5");
                  }

                  @DefaultValue.List(b__anotherfieldAsArray)
                  @Property(propertyName = "f")
                  public List<String> f() {
                      return Collections.singletonList("a6");
                  }

                  class B {
                      public static final String anotherField = "another_field";
                      public static final String anotherMultipleField = anotherField + ",another_other_field";
                      private String[] anotherMultipleFieldAsArray = anotherMultipleField.split(",");
                      private String[] constants__some_multiple_stringAsArray = Constants.SOME_MULTIPLE_STRING.split(",");

                      @DefaultValue.List(someFieldAsArray)
                      @Property(propertyName = "g")
                      public List<String> g() {
                          return Collections.singletonList("a7");
                      }

                      @DefaultValue.List(someMultipleFieldAsArray)
                      @Property(propertyName = "h")
                      public List<String> h() {
                          return Collections.singletonList("a8");
                      }

                      @DefaultValue.List(b__anotherfieldAsArray)
                      @Property(propertyName = "i")
                      public List<String> i() {
                          return Collections.singletonList("a9");
                      }

                      @DefaultValue.List(anotherMultipleFieldAsArray)
                      @Property(propertyName = "j")
                      public List<String> j() {
                          return Collections.singletonList("a10");
                      }

                      @DefaultValue.List(constants__some_stringAsArray)
                      @Property(propertyName = "k")
                      public List<String> k() {
                          return Collections.singletonList("a11");
                      }

                      @DefaultValue.List(constants__some_multiple_stringAsArray)
                      @Property(propertyName = "l")
                      public List<String> l() {
                          return Collections.singletonList("a12");
                      }
                  }
              }
              """
          )
        );
    }

    // TODO: Should we just always try to split the String even if we don't know it contains a comma?
    // TODO: different types of List<String>
    // TODO: String[]
    // TODO: Enum?
    // TODO: Set<String> and variations
}
