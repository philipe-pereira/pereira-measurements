package br.com.pereiraeng.measurements;

import br.com.pereiraeng.math.timeseries.unit.Meds;
import br.com.pereiraeng.xml.XMLadapter;

/**
 * Classe abstrata dos leitores de arquivos XML adaptada para a leitura de
 * medições.
 * 
 * @author Philipe PEREIRA
 *
 */
public abstract class XMLmedReader extends XMLadapter {

	/**
	 * Tempo entre uma tentativa e a próxima
	 */
	protected static final long WAIT = 2000L;

	/**
	 * Número máximo de tentativas
	 */
	protected static final int MAX = 3;

	/**
	 * Enumeração dos estados do leitor de dados dos arquivos XML retornados pelo
	 * sistema
	 * 
	 * @author Philipe PEREIRA
	 *
	 */
	protected enum ReadingStatus {
		/**
		 * medições não disponíveis
		 */
		UNAVAILABLE,
		/**
		 * medições foram solicitadas, mas elas ainda não foram retornadas
		 */
		WAITING,
		/**
		 * medições foram solicitadas e já estão disponíveis
		 */
		OK,
		/**
		 * medições foram solicitadas, mas recebeu-se uma mensagem de erro 1
		 */
		ERROR_1,
		/**
		 * medições foram solicitadas, mas recebeu-se uma mensagem de erro 2
		 */
		ERROR_2,
		/**
		 * medições foram solicitadas, mas recebeu-se uma mensagem de erro 3
		 */
		ERROR_3,
		/**
		 * medições foram solicitadas, mas recebeu-se uma mensagem de erro 4
		 */
		ERROR_4,
		/**
		 * medições foram solicitadas, mas recebeu-se uma mensagem de erro 5
		 */
		ERROR_5,
		/**
		 * medições foram solicitadas, mas recebeu-se uma mensagem de erro 6
		 */
		ERROR_6;
	}

	protected transient Meds[] meds;

	public XMLmedReader() {
		super();
	}

	public XMLmedReader(boolean stockable) {
		super(stockable);
	}
}
