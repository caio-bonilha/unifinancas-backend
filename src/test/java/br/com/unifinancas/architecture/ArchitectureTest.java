package br.com.unifinancas.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import br.com.unifinancas.domain.shared.RepositorioGlobal;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Arrays;

/**
 * As duas regras mais criticas do SAD (Sec15.5): a camada domain nunca conhece
 * infrastructure/presentation, e nenhum metodo de repositorio de dominio pode
 * ser declarado sem o parametro ambienteFinanceiroId (mecanismo de isolamento
 * entre tenants, Sec9.3/Sec12.4). Ambas falham o build de CI, nao sao checklist
 * manual de code review.
 */
@AnalyzeClasses(packages = "br.com.unifinancas", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domain_nao_depende_de_infrastructure_ou_presentation = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..", "..presentation..")
            .because("SAD Sec3.6/Sec9.1: o dominio nunca conhece infrastructure nem presentation");

    @ArchTest
    static final ArchRule repositorios_de_dominio_exigem_ambienteFinanceiroId = methods()
            .that().areDeclaredInClassesThat().resideInAPackage("..domain..")
            .and().areDeclaredInClassesThat().areInterfaces()
            .and().areDeclaredInClassesThat().haveSimpleNameEndingWith("Repository")
            .should(declararParametroAmbienteFinanceiroId())
            .because("SAD Sec9.3/Sec12.4: nenhum metodo de repositorio de dominio pode ser declarado "
                    + "sem o parametro ambienteFinanceiroId, o mecanismo de isolamento entre Ambientes "
                    + "Financeiros - a ausencia do parametro deve ser um erro de build, nao uma checagem "
                    + "em runtime que possa ser esquecida. Excecao deliberada: metodos ou interfaces "
                    + "anotados com @RepositorioGlobal (consultas que nao sao escopadas a um tenant, "
                    + "ex.: Usuario inteiro, ou so um metodo pontual como 'listar todos os ambientes "
                    + "de um usuario')")
            .allowEmptyShould(true);

    private static ArchCondition<JavaMethod> declararParametroAmbienteFinanceiroId() {
        return new ArchCondition<>("declarar um parametro ambienteFinanceiroId, a menos que o metodo ou a "
                + "interface esteja anotado com @RepositorioGlobal") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                boolean isento = method.isAnnotatedWith(RepositorioGlobal.class)
                        || method.getOwner().isAnnotatedWith(RepositorioGlobal.class);
                if (isento) {
                    return;
                }
                boolean declaraParametro = Arrays.stream(method.reflect().getParameters())
                        .anyMatch(parametro -> "ambienteFinanceiroId".equals(parametro.getName()));
                if (!declaraParametro) {
                    events.add(SimpleConditionEvent.violated(method,
                            method.getFullName() + " nao declara o parametro ambienteFinanceiroId"));
                }
            }
        };
    }
}
