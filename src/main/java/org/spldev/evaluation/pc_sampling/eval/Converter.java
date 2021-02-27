/* -----------------------------------------------------------------------------
 * Evaluation-PC-Sampling - Program for the evalaution of PC-Sampling.
 * Copyright (C) 2021  Sebastian Krieter
 * 
 * This file is part of Evaluation-PC-Sampling.
 * 
 * Evaluation-PC-Sampling is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 * 
 * Evaluation-PC-Sampling is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with Evaluation-PC-Sampling.  If not, see <https://www.gnu.org/licenses/>.
 * 
 * See <https://github.com/skrieter/evaluation-pc-sampling> for further information.
 * -----------------------------------------------------------------------------
 */
package org.spldev.evaluation.pc_sampling.eval;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.spldev.evaluation.pc_sampling.eval.analyzer.FileProvider;
import org.spldev.evaluation.pc_sampling.eval.analyzer.PresenceCondition;
import org.spldev.evaluation.pc_sampling.eval.analyzer.PresenceConditionList;
import org.spldev.formula.VariableMap;
import org.spldev.formula.clause.CNF;
import org.spldev.formula.clause.ClauseList;
import org.spldev.formula.clause.Clauses;
import org.spldev.formula.clause.LiteralList;
import org.spldev.formula.clause.LiteralList.Order;
import org.spldev.formula.expression.Expression;
import org.spldev.formula.expression.Formula;
import org.spldev.formula.expression.Formulas;
import org.spldev.formula.expression.Formulas.NormalForm;
import org.spldev.formula.expression.atomic.literal.Literal;
import org.spldev.formula.expression.compound.And;
import org.spldev.formula.expression.compound.Or;
import org.spldev.formula.expression.io.parse.NodeReader;
import org.spldev.formula.expression.io.parse.NodeReader.ErrorHandling;
import org.spldev.formula.expression.io.parse.Symbols;
import org.spldev.formula.expression.io.parse.Symbols.Operator;
import org.spldev.tree.Trees;
import org.spldev.util.logging.Logger;

public class Converter {

	private static class TempPC {
		private final String formulaString;
		private final Formula formula;
		private final Path sourceFilePath;

		public TempPC(String formulaString, Formula formula, Path sourceFilePath) {
			this.formulaString = formulaString;
			this.formula = formula;
			this.sourceFilePath = sourceFilePath;
		}
	}

	private NodeReader nodeReader;
	private CNF fmFormula;
	private Path systemPath;

	private long counter;

	public Converter(CNF fmFormula, String completeName) {
		this.fmFormula = fmFormula;
		systemPath = Constants.systems.resolve(completeName).toAbsolutePath().normalize();

		nodeReader = new NodeReader();
		final Symbols symbols = new Symbols();
		symbols.setSymbol(Operator.NOT, "!");
		symbols.setSymbol(Operator.AND, "&&");
		symbols.setSymbol(Operator.OR, "||");
		symbols.setTextual(false);
		nodeReader.setSymbols(symbols);
		nodeReader.setIgnoreMissingFeatures(ErrorHandling.REMOVE);
		nodeReader.setIgnoreUnparsableSubExpressions(ErrorHandling.REMOVE);
		if (fmFormula != null) {
			nodeReader.setVariableNames(fmFormula.getVariableMap().getNames());
		} else {
			nodeReader.setVariableNames(null);
		}
	}

	public PresenceConditionList convert() {
		final Path path = Constants.presenceConditionsOutput.resolve(systemPath.getFileName());
		if (!Files.isReadable(path)) {
			return null;
		}
		final FileProvider fileProvider = new FileProvider(path);
		fileProvider.setFileNameRegex(FileProvider.PCFileRegex);

		final Collection<String> pcNames = new LinkedHashSet<>();
		final long fileCount = fileProvider.getFileStream().count();
		counter = 0;
		final List<TempPC> pcFormulas = fileProvider.getFileStream() //
				.flatMap(p -> {
					Logger.logProgress("(" + ++counter + "/" + fileCount + ") " + p.toString());
					List<String> lines;
					try {
						lines = Files.readAllLines(p);
					} catch (final IOException e) {
						return Stream.empty();
					}
					final Path sourceFilePath = Paths.get(lines.get(0));

					return lines.subList(1, lines.size()).stream() //
							.filter(expr -> !"null".equals(expr)).distinct() //
							.map(expr -> {
								final Formula formula = nodeReader.read(expr).get();
								if (formula == null) {
									return null;
								} else {
									pcNames.addAll(Formulas.getVariables(formula));
									return new TempPC(expr, formula, sourceFilePath);
								}
							})
							.filter(Objects::nonNull) //
					;
				}).collect(Collectors.toList());

		final CNF modelFormula = fmFormula != null ? fmFormula : new CNF(new VariableMap(pcNames));

		final HashMap<String, PresenceCondition> pcMap = new HashMap<>();
		final int pcCount = pcFormulas.size();
		counter = 0;
		final List<PresenceCondition> convertedPCs = pcFormulas.stream() //
				.map(tempPC -> {
					Logger.logProgress(++counter + "/" + pcCount + " - " + tempPC.formulaString);
					PresenceCondition pc = pcMap.get(tempPC.formulaString);
					if (pc == null) {
						final VariableMap variableMap = modelFormula.getVariableMap();
						final ClauseList clauses = new ClauseList();
						final CNF dnf;
						final CNF negatedDnf;
						if (Formulas.checkNormalForm(tempPC.formula, NormalForm.CNF)) {
							Formula cnfFormula = Trees.cloneTree(tempPC.formula);
							cnfFormula.mapChildren(c -> (c instanceof Literal) ? new Or((Literal) c) : null);
							cnfFormula.getChildren().stream().map(exp -> getClause(exp, variableMap))
									.filter(Objects::nonNull).forEach(clauses::add);
							final CNF cnf = new CNF(variableMap, clauses);
							dnf = new CNF(variableMap, Clauses.convertNF(cnf.getClauses()));
							negatedDnf = new CNF(variableMap, cnf.getClauses().negate());
						} else if (Formulas.checkNormalForm(tempPC.formula, NormalForm.DNF)) {
							Formula dnfFormula = Trees.cloneTree(tempPC.formula);
							dnfFormula.mapChildren(c -> (c instanceof Literal) ? new And((Literal) c) : null);
							dnfFormula.getChildren().stream().map(exp -> getClause(exp, variableMap))
									.filter(Objects::nonNull).forEach(clauses::add);
							dnf = new CNF(variableMap, clauses);
							final CNF cnf = new CNF(variableMap, Clauses.convertNF(dnf.getClauses()));
							negatedDnf = new CNF(variableMap, cnf.getClauses().negate());
						} else {
							Formula simplifiedFormula = Formulas.simplifyForNF(tempPC.formula);
							if (simplifiedFormula instanceof Or) {
								Formula dnfFormula = Formulas.distributiveLawTransform(simplifiedFormula, NormalForm.DNF);
								dnfFormula.mapChildren(c -> (c instanceof Literal) ? new And((Literal) c) : null);
								dnfFormula.getChildren().stream().map(exp -> getClause(exp, variableMap))
										.filter(Objects::nonNull).forEach(clauses::add);
								dnf = new CNF(variableMap, clauses);
								final CNF cnf = new CNF(variableMap, Clauses.convertNF(dnf.getClauses()));
								negatedDnf = new CNF(variableMap, cnf.getClauses().negate());
							} else if (simplifiedFormula instanceof And) {
								Formula cnfFormula = Formulas.distributiveLawTransform(simplifiedFormula, NormalForm.CNF);
								cnfFormula.mapChildren(c -> (c instanceof Literal) ? new Or((Literal) c) : null);
								cnfFormula.getChildren().stream().map(exp -> getClause(exp, variableMap))
										.filter(Objects::nonNull).forEach(clauses::add);
								final CNF cnf = new CNF(variableMap, clauses);
								dnf = new CNF(variableMap, Clauses.convertNF(cnf.getClauses()));
								negatedDnf = new CNF(variableMap, cnf.getClauses().negate());
							} else if (simplifiedFormula instanceof Literal) {
								final LiteralList clause = getClause(simplifiedFormula, variableMap);
								if (clause != null) {
									clauses.add(clause);
								}
								final CNF cnf = new CNF(variableMap, clauses);
								dnf = new CNF(variableMap, Clauses.convertNF(cnf.getClauses()));
								negatedDnf = new CNF(variableMap, cnf.getClauses().negate());
							} else {
								pc = new PresenceCondition();
								pcMap.put(tempPC.formulaString, pc);
								return pc;
							}
						}
						if (negatedDnf.getClauses().isEmpty() || (negatedDnf.getClauses().get(0).size() == 0)
								|| dnf.getClauses().isEmpty() || (dnf.getClauses().get(0).size() == 0)) {
							pc = new PresenceCondition();
						} else {
							pc = new PresenceCondition(tempPC.sourceFilePath, dnf, negatedDnf);
						}
						pcMap.put(tempPC.formulaString, pc);
						return pc;
					} else {
						if (pc.getDnf() != null) {
							return new PresenceCondition(tempPC.sourceFilePath, pc.getDnf(), pc.getNegatedDnf());
						} else {
							return pc;
						}
					}
				}).filter(pc -> pc.getDnf() != null).collect(Collectors.toList());

		final PresenceConditionList presenceConditionList = new PresenceConditionList(convertedPCs, modelFormula);
		presenceConditionList.setPCNames(new ArrayList<>(pcNames));

		return presenceConditionList;
	}

	private LiteralList getClause(Expression clauseExpression, VariableMap mapping) {
		if (clauseExpression instanceof Literal) {
			final Literal literal = (Literal) clauseExpression;
			final int variable = mapping.getIndex(literal.getName()).orElseThrow(() -> new RuntimeException(literal.getName()));
			return new LiteralList(new int[] { literal.isPositive() ? variable : -variable }, Order.NATURAL, false);
		} else {
			final List<? extends Expression> clauseChildren = clauseExpression.getChildren();
			if (clauseChildren.stream().anyMatch(literal -> literal == Literal.True)) {
				return null;
			} else {
				final int[] literals = clauseChildren.stream().filter(literal -> literal != Literal.False)
						.mapToInt(literal -> {
							final int variable = mapping.getIndex(literal.getName()).orElseThrow(() -> new RuntimeException(literal.getName()));
							return ((Literal) literal).isPositive() ? variable : -variable;
						}).toArray();
				return new LiteralList(literals, Order.NATURAL).clean();
			}
		}
	}

}