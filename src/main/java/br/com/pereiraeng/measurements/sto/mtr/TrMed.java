package br.com.pereiraeng.measurements.sto.mtr;

import br.com.pereiraeng.math.timeseries.unit.Med;
import br.com.pereiraeng.math.timeseries.unit.MedH;
import br.com.pereiraeng.math.timeseries.unit.TData;

/**
 * <p>
 * Classe das funções que manipulam os arquivos MTR, desenvolvidos especialmente
 * para estocagem de informação por curto-período, no auxílio à operação em
 * <strong>tempo real</strong>.
 * </p>
 * 
 * <p>
 * As medições são associadas a uma dada tag (num dado padrão), com a data, o
 * valor e os meta-dados associados (e.g., tempo em que ela está congelada neste
 * valor).
 * </p>
 * 
 * <p>
 * Este arquivo tem em seu cabeçalho:
 * </p>
 * 
 * <ul>
 * <li>4 bytes: a última vez que este arquivo sofreu qualquer alteração;</i>
 * <li>4 bytes: a última vez que a ordem das tags foi alterada, de modo que os
 * clientes saberão quando houve;</i>
 * <li>2 bytes: quantidade de blocos;</i>
 * <li>blocos de tags.</i>
 * <ul>
 * <li>2 bytes para o número de tags no bloco;</i>
 * <li>8 bytes nome do bloco;</i>
 * <li>2 bytes para a frequência com que o bloco é atualizado, em segundos;</i>
 * <li>2 bytes para a quantidade de medições que são mantidas em cache.</i>
 * </ul>
 * </ul>
 * 
 * <ul>
 * <li>32 bytes tag no padrão do estimador de estados, do qual seja possível
 * concluir até mesmo o sentido do fluxo</i>
 * <li>4 bytes inteiro da data e hora</i>
 * <li>4 bytes float do valor medido</i>
 * <li>4 bytes float do valor estimado (se houver)</i>
 * <li>4 bytes com o tempo, em segundos, em que não houve alteração neste
 * valor</i>
 * <li>2 bytes máscara de dados (entre as flags, tem a indicação se o valor
 * estimado foi dado por estimação de estados ou outra expressão livre)</i>
 * </ul>
 * 
 * <p>
 * Totalizando 50 bytes por tag por instante de tempo.
 * </p>
 * 
 * <p>
 * O arquivo terá exatamente<br>
 * 4 + 2 + 12*g + 50*t bytes<br>
 * onde g é o número de grupos e t o número total de tags
 * </p>
 * 
 * <p>
 * A tag poderá ser localizada de três maneiras:
 * </p>
 * 
 * <ul>
 * <li>pela sequência de caracteres;</i>
 * <li>pelos índices do bloco e da tag no bloco;</i>
 * <li>pelo índice absoluto da tag.</i>
 * </ul>
 * 
 * <p>
 * É possível baixar todas as tags de um bloco de uma vez.
 * </p>
 * 
 * <p>
 * Cada tag padrão, com seus 32 bytes, indica de maneira unívoca:
 * <ul>
 * <li>grandeza medida, no padrão da ontologia MinPot;</i>
 * <li>a instalação;</i>
 * <li>o ponto da instalação onde está sendo medido (identificação do vão);</i>
 * <li>o sentido;</i>
 * <li>(opcional) o equipamento medido (trafo ou linha).</i>
 * </ul>
 * </p>
 * 
 * Além disso, ela tem outros meta-dados:
 * <ul>
 * <li>expressão alternativa para ser estimada;</i>
 * <li>opcional: alarmes condicionais (sobre os valor ou sobre o tempo de
 * congelamento) para indicação de falha de medição;</i>
 * <li>transformação afim do valor.</i>
 * </ul>
 * 
 * @author Philipe PEREIRA
 * @version January 14th, 2021
 *
 */
public class TrMed {
	// ---------- FROM MED ----------

	public byte[] fromMed(TData m) {
		if (m instanceof MedH) {
//			MedH med = (MedH) m;

		} else {

		}
		return null;
	}

	public void fromMed(TData m, byte[] array, int pos) {

	}

	// ---------- TO MED ----------

	public MedH toXMed(byte[] m) {

		return null;
	}

	public MedH toXMed(byte[] array, int pos) {
		return null;

	}

	public TData toMed(byte[] m) {

		return null;
	}

	public Med toMed(byte[] array, int pos) {
		return null;

	}
}
