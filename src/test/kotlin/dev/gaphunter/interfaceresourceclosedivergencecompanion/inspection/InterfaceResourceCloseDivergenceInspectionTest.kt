package dev.gaphunter.interfaceresourceclosedivergencecompanion.inspection

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** Every test method uses its own uniquely-suffixed class/interface names -- same discipline as this catalog's other cross-implementation test suites. */
class InterfaceResourceCloseDivergenceInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(InterfaceResourceCloseDivergenceInspection::class.java)
    }

    fun `test implementations that diverge on closing the resource are flagged`() {
        myFixture.configureByText(
            "HandlerA.java",
            """
            import java.sql.Connection;

            interface ConnectionHandlerA {
                void handle(Connection conn) throws Exception;
            }

            class ClosingHandlerA implements ConnectionHandlerA {
                public void handle(Connection conn) throws Exception {
                    conn.createStatement();
                    conn.close();
                }
            }

            class LeakyHandlerA implements ConnectionHandlerA {
                public void handle(Connection conn) throws Exception {
                    conn.createStatement();
                }
            }

            class CallerA {
                void run(ConnectionHandlerA handler, Connection conn) throws Exception {
                    handler.handle(conn);
                }
            }
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.any { it.description?.contains("ConnectionHandlerA") == true && it.description?.contains("CWE-772") == true })
    }

    fun `test implementations that both close the resource consistently are not flagged`() {
        myFixture.configureByText(
            "HandlerB.java",
            """
            import java.sql.Connection;

            interface ConnectionHandlerB {
                void handle(Connection conn) throws Exception;
            }

            class ClosingHandlerB1 implements ConnectionHandlerB {
                public void handle(Connection conn) throws Exception {
                    conn.close();
                }
            }

            class ClosingHandlerB2 implements ConnectionHandlerB {
                public void handle(Connection conn) throws Exception {
                    conn.createStatement();
                    conn.close();
                }
            }

            class CallerB {
                void run(ConnectionHandlerB handler, Connection conn) throws Exception {
                    handler.handle(conn);
                }
            }
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.none { it.description?.contains("ConnectionHandlerB") == true })
    }

    fun `test implementations that never close the resource are not flagged`() {
        myFixture.configureByText(
            "HandlerC.java",
            """
            import java.sql.Connection;

            interface ConnectionHandlerC {
                void handle(Connection conn) throws Exception;
            }

            class LeakyHandlerC1 implements ConnectionHandlerC {
                public void handle(Connection conn) throws Exception {}
            }

            class LeakyHandlerC2 implements ConnectionHandlerC {
                public void handle(Connection conn) throws Exception {}
            }

            class CallerC {
                void run(ConnectionHandlerC handler, Connection conn) throws Exception {
                    handler.handle(conn);
                }
            }
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.none { it.description?.contains("ConnectionHandlerC") == true })
    }

    fun `test a call through the CONCRETE type is not flagged`() {
        myFixture.configureByText(
            "HandlerD.java",
            """
            import java.sql.Connection;

            interface ConnectionHandlerD {
                void handle(Connection conn) throws Exception;
            }

            class ClosingHandlerD implements ConnectionHandlerD {
                public void handle(Connection conn) throws Exception {
                    conn.close();
                }
            }

            class LeakyHandlerD implements ConnectionHandlerD {
                public void handle(Connection conn) throws Exception {}
            }

            class CallerD {
                void run(ClosingHandlerD handler, Connection conn) throws Exception {
                    handler.handle(conn);
                }
            }
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.none { it.description?.contains("ConnectionHandlerD") == true })
    }

    fun `test an interface with only one real implementation is not flagged`() {
        myFixture.configureByText(
            "HandlerE.java",
            """
            import java.sql.Connection;

            interface ConnectionHandlerE {
                void handle(Connection conn) throws Exception;
            }

            class OnlyHandlerE implements ConnectionHandlerE {
                public void handle(Connection conn) throws Exception {
                    conn.close();
                }
            }

            class CallerE {
                void run(ConnectionHandlerE handler, Connection conn) throws Exception {
                    handler.handle(conn);
                }
            }
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.none { it.description?.contains("ConnectionHandlerE") == true })
    }
}
