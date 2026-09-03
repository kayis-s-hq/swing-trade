package com.swingtrade.data.entity;

import com.swingtrade.domain.BacktestResult;
import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name="backtest_result", uniqueConstraints=@UniqueConstraint(name="uq_backtest_result_symbol_date", columnNames={"symbol","run_date"}))
public class BacktestResultEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private String symbol;
    @Column(name="run_date") private LocalDate runDate;
    private int totalTrades, winningTrades, losingTrades;
    private double winRate, avgGainPct, avgLossPct, maxDrawdownPct, sharpeRatio, totalReturn, expectancy, profitFactor;
    private boolean hasEnoughData;
    public BacktestResultEntity() {}
    public static BacktestResultEntity fromDomain(BacktestResult r) { var e=new BacktestResultEntity(); e.id=r.id(); e.symbol=r.symbol(); e.runDate=r.runDate(); e.totalTrades=r.totalTrades(); e.winningTrades=r.winningTrades(); e.losingTrades=r.losingTrades(); e.winRate=r.winRate(); e.avgGainPct=r.avgGainPct(); e.avgLossPct=r.avgLossPct(); e.maxDrawdownPct=r.maxDrawdownPct(); e.sharpeRatio=r.sharpeRatio(); e.totalReturn=r.totalReturn(); e.expectancy=r.expectancy(); e.profitFactor=r.profitFactor(); e.hasEnoughData=r.hasEnoughData(); return e; }
    public BacktestResult toDomain(){return new BacktestResult(id,symbol,runDate,totalTrades,winningTrades,losingTrades,winRate,avgGainPct,avgLossPct,maxDrawdownPct,sharpeRatio,totalReturn,expectancy,profitFactor,hasEnoughData);}
    public Long getId(){return id;} public void setId(Long v){id=v;} public String getSymbol(){return symbol;} public void setSymbol(String v){symbol=v;} public LocalDate getRunDate(){return runDate;} public void setRunDate(LocalDate v){runDate=v;}
    public int getTotalTrades(){return totalTrades;} public void setTotalTrades(int v){totalTrades=v;} public int getWinningTrades(){return winningTrades;} public void setWinningTrades(int v){winningTrades=v;} public int getLosingTrades(){return losingTrades;} public void setLosingTrades(int v){losingTrades=v;} public double getWinRate(){return winRate;} public void setWinRate(double v){winRate=v;} public double getAvgGainPct(){return avgGainPct;} public void setAvgGainPct(double v){avgGainPct=v;} public double getAvgLossPct(){return avgLossPct;} public void setAvgLossPct(double v){avgLossPct=v;} public double getMaxDrawdownPct(){return maxDrawdownPct;} public void setMaxDrawdownPct(double v){maxDrawdownPct=v;} public double getSharpeRatio(){return sharpeRatio;} public void setSharpeRatio(double v){sharpeRatio=v;} public double getTotalReturn(){return totalReturn;} public void setTotalReturn(double v){totalReturn=v;} public double getExpectancy(){return expectancy;} public void setExpectancy(double v){expectancy=v;} public double getProfitFactor(){return profitFactor;} public void setProfitFactor(double v){profitFactor=v;} public boolean isHasEnoughData(){return hasEnoughData;} public void setHasEnoughData(boolean v){hasEnoughData=v;}
}
