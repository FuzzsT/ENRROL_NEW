package io.dpcaio.network

fun main() {
    val rules = DnsRuleSet(listOf(
        DnsRule("lab.local", DnsAction.Override("10.10.0.20")),
        DnsRule("*.telemetry.vendor.com", DnsAction.Block),
        DnsRule("exact.example", DnsAction.Override("192.0.2.1"))
    ))
    check(rules.resolve("LAB.LOCAL") == DnsAction.Override("10.10.0.20"))
    check(rules.resolve("a.telemetry.vendor.com") == DnsAction.Block)
    check(rules.resolve("telemetry.vendor.com") == null)
    check(rules.resolve("unknown.example") == null)

    val planner = DnsPolicyPlanner()
    check(planner.plan(DnsPolicyInput(deviceOwner=true, specifiedPrivateDnsSupported=true, dohDiagnosticEnabled=true)) == DnsRoute.PRIVATE_DNS_DOT)
    check(planner.plan(DnsPolicyInput(deviceOwner=false, specifiedPrivateDnsSupported=false, dohDiagnosticEnabled=true)) == DnsRoute.DOH_DIAGNOSTIC)
    println("DnsPolicyTest: PASS")
}
