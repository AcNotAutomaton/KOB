## 解释与风险说明

Spring Cloud 2022.0.x（又称 Kilburn/2022.0）是为 Spring Boot 3.x 系列设计的。3.1.6 与 2022.0.5 在常见组合下是兼容的；因此简单升级 BOM 到 2022.0.5 通常是安全的。
虽然构建与依赖解析通过，但 Spring Cloud 的运行时特性（例如 Spring Cloud Config、Eureka、Consul、Stream、Circuit Breaker 等）在不同次要/补丁版本间偶尔会有行为差异。若你的项目中使用了 Spring Cloud 的具体模块（在当前代码里未见明确模块依赖），建议在一个受控环境运行集成测试或 smoke tests 以确认运行时行为无误。
如果你使用 Spring Cloud Gateway / Stream / Sleuth / Config 等子项目，请告知，我会针对这些模块检查具体版本和兼容性说明，并在必要时建议单独升级对应模块版本或调整配置。


git commit -m "Upgrade Spring Boot to 3.1.6, Spring Cloud BOM to 2022.0.5; fix JwtUtil and add unit test"


