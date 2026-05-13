package br.com.pereiraeng.measurements;

import br.com.pereiraeng.core.TimeUtils;
import br.com.pereiraeng.math.timeseries.Reg;
import br.com.pereiraeng.measurements.label.MedEtq;

/**
 * Classe do objeto que contém informações relativas às correções que devem ser
 * aplicadas a uma dada tag de medição
 * 
 * @author Philipe PEREIRA
 *
 */
public class Factor {

	/**
	 * Inteiro que designa o minuto a partir do qual será feito a correção (ver
	 * {@link TimeUtils#toInt(java.util.Calendar)})
	 */
	private int begin;

	/**
	 * Inteiro que designa o minuto até o qual será aplicado a correção (ver
	 * {@link TimeUtils#toInt(java.util.Calendar)})
	 */
	private int end;

	/**
	 * <ul>
	 * <li>+: soma</i>
	 * <li>*: multiplicação</i>
	 * <li>s: substituição</i>
	 * </ul>
	 */
	private char operation;

	private float factor;

	/**
	 * Tag a ser acompanhada
	 */
	private MedEtq tagAcomp;

	/**
	 * Construtor do objeto que representa fatores de correção para as tags
	 * 
	 * @param begin  inteiro que representa o instante inicial para o qual as
	 *               correções devem ser aplicadas (ver
	 *               {@link TimeUtils#toCalendar(int)} )
	 * @param end    inteiro que representa o instante final para o qual as
	 *               correções devem ser aplicadas (ver
	 *               {@link TimeUtils#toCalendar(int)} )
	 * @param factor número decimal que indica o fator de correção
	 */
	public Factor(int begin, int end, float factor) {
		this.begin = begin;
		this.end = end;
		this.factor = factor;
	}

	@Override
	public String toString() {
		if (isAllTime())
			return String.format("todo período; %c %g", operation, factor);
		else {
			String s1 = begin == -1 ? "..." : String.format("%1$td/%1$tm/%1$tY %1$tH:%1$tM", TimeUtils.toCalendar(begin));
			String s2 = end == -1 ? "..." : String.format("%1$td/%1$tm/%1$tY %1$tH:%1$tM", TimeUtils.toCalendar(end));
			return String.format("%s - %s; %c %g", s1, s2, operation, factor);
		}
	}

	public int getBegin() {
		return begin == -1 ? Integer.MIN_VALUE : begin;
	}

	public void setBegin(int begin) {
		this.begin = begin;
	}

	public int getEnd() {
		return end == -1 ? Integer.MAX_VALUE : end;
	}

	public void setEnd(int end) {
		this.end = end;
	}

	public boolean isAllTime() {
		return begin == -1 && end == -1;
	}

	public float getFactor() {
		return factor;
	}

	public void setFactor(float factor) {
		this.factor = factor;
	}

	public void setFactor(double factor) {
		this.factor = (float) factor;
	}

	/**
	 * Função que retorna o tipo de operação que está sendo feita por este
	 * 
	 * @return
	 *         <ul>
	 *         <li>+: soma</i>
	 *         <li>*: multiplicação</i>
	 *         <li>s: substituição</i>
	 *         </ul>
	 */
	public char getOperation() {
		return operation;
	}

	/**
	 * Função em que é estabelecida o tipo de operação que está sendo feita por este
	 * fator
	 * 
	 * @param oper
	 *             <ul>
	 *             <li>S ou +: soma</i>
	 *             <li>M ou *: multiplicação</i>
	 *             <li>s: substituição</i>
	 *             </ul>
	 */
	public void setOperation(char oper) {
		this.operation = (oper == 'S' ? '+' : (oper == 'M' ? '*' : oper));
	}

	public MedEtq getTagAcomp() {
		return tagAcomp;
	}

	public void setTagAcomp(MedEtq refTag) {
		this.tagAcomp = refTag;
	}

	/**
	 * Função que indica o tipo de fator de correção. A letra coincide com a letra
	 * final do nome da tabela SQL que armazena tais fatores de correção
	 * 
	 * @return
	 *         <ul>
	 *         <li>e: substituição;</i>
	 *         <li>c: operação;</i>
	 *         <li>d: acompanhamento.</i>
	 *         </ul>
	 */
	public char getFactorType() {
		if (this.operation == 's') {
			// substituição
			return 'e';
		} else {
			if (tagAcomp == null) {
				// operação
				return 'c';
			} else {
				// acompanhamento
				return 'd';
			}
		}
	}

	// ----------------------- aplicação -----------------------

	/**
	 * Função que aplica um fator de correção sobre um dado conjunto de medições
	 * 
	 * @param r      objeto contendo as medições a serem corrigidas
	 * @param pos    posição no objeto de registro onde estão os dados a serem
	 *               corrigidos
	 * @param factor objeto contendo as informações relativas ao tipo de correção a
	 *               ser efetuado
	 */
	public void apply(Reg r, int pos) {
		if (getOperation() == 's') {
			// se for substituição
			Reg.replace(r, pos, getFactor(), getBegin(), getEnd());
		} else {
			// se for operação
			Reg o = new Reg(r.length());
			Reg.operation(o, r, pos, getOperation(), getFactor(), getBegin(), getEnd());
			Reg.transfer(o, 0, r, pos);
		}
	}

	// ------------------------------- SQL -------------------------------

	/**
	 * TODO coisa da Cemig
	 * @param refTag referência da tag alvo
	 * @return comando SQL
	 */
	public String getSQLinsert(int refTag) {
		char type = getFactorType();
		switch (type) {
		case 'e': // substituição
			return "INSERT INTO `tag_1e`(`ref_tag`, `begin`, `end`, `factor`) VALUES (" + refTag + "," + begin + ","
					+ end + "," + factor + ")";
		case 'c': // operação
			return "INSERT INTO `tag_1c`(`ref_tag`, `begin`, `end`, `factor`, " + "`ope`) VALUES (" + refTag + ","
					+ begin + "," + end + "," + factor + ", '" + (this.operation == '+' ? "SOMA" : "MULT") + "')";
		case 'd': // acompanhamento
			return "INSERT INTO `tag_1d`(`ref_tag`, `begin`, `end`, `factor`, " + "`ope`, `ref_2`) " + "VALUES ("
					+ refTag + "," + begin + "," + end + "," + factor + ", '"
					+ (this.operation == '+' ? "SOMA" : "MULT") + "'," + this.tagAcomp.getExternalId() + ")";
		}
		return null;
	}

	/**
	 * 
	 * @param refTag referência da tag alvo
	 * @return comando SQL
	 */
	public String deleteSQL(int refTag) {
		return "DELETE FROM `tag_1" + getFactorType() + "` WHERE `ref_tag`=" + refTag + " AND `begin`=" + begin
				+ " AND `end`=" + end;
	}
}