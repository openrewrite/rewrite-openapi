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
                      public static String SOME_CHAR = "c";
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
//          //language=java
//          java(
//            """
//              import x.y.z.Constants;
//              """
//          ),
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
}
