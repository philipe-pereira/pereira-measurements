package br.com.pereiraeng.measurements.label;

import java.io.Serializable;

import br.com.pereiraeng.electricalengineering.Tensao;
import br.com.pereiraeng.physics.Propriedade;

/**
 * <p>
 * Classe do objeto da etiqueta de uma medição: uma sequência de caracteres que
 * caracterizam de maneira {@link #equals(Object) unívoca} uma grandeza medida
 * ao longo do tempo.
 * </p>
 * <p>
 * A designação é dada pelo <strong>hóspede</strong>: alguns dados poderão ter
 * sido definidos pelo usuário, e alguns metadados são requeridos (devendo ser
 * fornecidos, seja por Pattern, seja manualmente). Além disso, o objeto tem
 * estruturas herdadas de {@link MedEtq} para guardar dados do hospedeiro
 * </p>
 * 
 * @author Philipe PEREIRA
 * @version July 08, 2020
 */
public class EltMedEtq extends MedEtq implements Serializable {
	private static final long serialVersionUID = -1563111595972810522L;

	/**
	 * id na base local, -1 se for a mesma {@link #getExternalId() do externo}
	 */
	private int localId;

	// equipameno medido

	/**
	 * número que designa o tipo de equipamento que é o proprietário da label
	 */
	private byte type;

	/**
	 * número de referência do equipamento que é o proprietário da label
	 */
	private int equip;

	/**
	 * identificação do vão
	 */
	private byte vao;

	/**
	 * identificação do terminal
	 */
	private byte tmId;

	// medição

	/**
	 * <ol start="0">
	 * <li>Medida;</i>
	 * <li>Estimada.</i>
	 * </ol>
	 */
	private byte origem;

	/**
	 * Designação do nível de tensão para grandeza
	 */
	private Tensao tensao;

	/**
	 * @param tag        sequência de caracteres que caracterizam de maneira
	 *                   {@link #equals(Object) unívoca} algo
	 * @param externalId id na base de dados externa, -1 se não houver identificação
	 * @param source     id do sistema em que ela é válida
	 * @param localId    id na base local, -1 se for a mesma {@link #getExternalId()
	 *                   do externo}
	 * @param space      número de referência da instalação que é a proprietária da
	 *                   label
	 * @param type       número que designa o tipo de equipamento que é o
	 *                   proprietário da label
	 * @param equip      número de referência do equipamento que é o proprietário da
	 *                   label
	 * @param vao        identificação do vão
	 * @param tmId       número que designa o tipo de equipamento que é o
	 *                   proprietário da label
	 * @param grand      grandeza medida pela tag (analógicas ou digitais)
	 * @param origem
	 *                   <ol start="0">
	 *                   <li>Medida;</i>
	 *                   <li>Estimada.</i>
	 *                   </ol>
	 * @param tensao     designação do nível de tensão para grandezas e tensão, -1
	 *                   se não for aplicável
	 */
	public EltMedEtq(String tag, int externalId, byte source, int localId, int space, byte type, int equip, byte vao,
			byte tmId, Propriedade grand, byte origem, Tensao tensao) {
		super(tag, externalId, source, space, grand);
		this.localId = localId;
		this.type = type;
		this.equip = equip;
		this.vao = vao;
		this.tmId = tmId;
		this.origem = origem;
		this.tensao = tensao;
	}

	public int getLocalId() {
		return localId;
	}

	public void setLocalId(int localId) {
		this.localId = localId;
	}

	public byte getType() {
		return type;
	}

	public void setType(byte type) {
		this.type = type;
	}

	public int getEquip() {
		return equip;
	}

	public void setEquip(int equip) {
		this.equip = equip;
	}

	public byte getVao() {
		return vao;
	}

	public byte getTmId() {
		return tmId;
	}

	public byte getOrigem() {
		return origem;
	}

	public void setOrigem(byte origem) {
		this.origem = origem;
	}

	public Tensao getTensao() {
		return tensao;
	}

	public void setTensao(Tensao tensao) {
		this.tensao = tensao;
	}
}
