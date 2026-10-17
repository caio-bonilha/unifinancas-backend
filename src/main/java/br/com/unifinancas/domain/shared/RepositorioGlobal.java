package br.com.unifinancas.domain.shared;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca um repositorio de dominio (ou um metodo especifico dele) cuja consulta
 * NAO e escopada a um Ambiente Financeiro - ex.: {@code UsuarioRepository}
 * inteiro (identidade global, SAD Sec4.1), ou so um metodo pontual como
 * {@code UsuarioAmbienteFinanceiroRepository.findAllByUsuarioIdAndStatus}
 * (descobrir TODOS os ambientes de um usuario e inerentemente cross-tenant,
 * mesmo em uma interface cujos OUTROS metodos sao corretamente escopados).
 * Isenta da regra de ArchUnit que exige {@code ambienteFinanceiroId} em todo
 * metodo de repositorio (T0.2.1, SAD Sec9.3). Uso deliberado e raro: exige o
 * motivo explicito para nao virar uma saida facil que esconde um vazamento
 * real entre tenants (o proprio ponto da regra que esta sendo flexibilizada).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface RepositorioGlobal {

    String motivo();
}
