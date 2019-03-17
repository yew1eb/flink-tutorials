
## 输入表
### app.hbdata_hotel_order_pay

```
{
    "order_time_30min":"2018-09-11 11:30:00",
    "location":"29.585950385440984,106.52642151572876",
    "region_id":"3031",
    "employee_organization":"重庆分区一组",
    "brand_name":"",
    "hour_room":"DR",
    "region_name":"销售四区",
    "client_type":"android",
    "order_time":"2018-09-11 11:56:36",
    "poi_name":"威斯曼艺术酒店（观音桥店）",
    "longitude":"106532232",
    "department_name":"简约住宿商务中心",
    "order_time_1hour":"2018-09-11 11:00:00",
    "partner_id":"4054762",
    "dp_id":"",
    "zone_id":"3067",
    "brand_id":"-1",
    "city_location_id":"500100",
    "barea_name":"观音桥",
    "hotel_type":"[0]",
    "is_new_user":"0",
    "order_datekey":"20180911",
    "dp_city_id":"9",
    "busi_type":"pp",
    "goods_id":"6708323",
    "is_ka":"0",
    "order_time_5min":"2018-09-11 11:55:00",
    "bd_mis":"baohengzhen",
    "uuid":"53F533EBE25DB7B92AC39AA379D94CC298670B63DE022A02C291B83C822D125B",
    "latitude":"29572498",
    "team_id":"2757",
    "organization_name":"重庆分区一组",
    "version_name":"9.6.6",
    "city_location_name":"重庆市",
    "order_timestamp":"1536638196000",
    "hotel_group_id":"-1",
    "hotel_group_name":"",
    "department_id":"1855",
    "order_id":"883694246",
    "poi_id":"159537397",
    "hotel_star":"6",
    "city_id":"45",
    "order_key":"2002883694246",
    "bd_id":"2087881",
    "zone_name":"重庆分区",
    "is_high_star":"0",
    "user_id":"486396204",
    "dp_shop_id":"92750197",
    "checkin_timestamp":"1536595200000",
    "location_name":"江北区",
    "checkout_timestamp":"1536681600000",
    "team_name":"重庆分区一组",
    "bd_name":"包恒珍",
    "province_location_id":"500000",
    "location_id":"500105",
    "checkout_time":"2018-09-12 00:00:00",
    "checkin_time":"2018-09-11 00:00:00",
    "organization_id":"2757",
    "partner_type":"0",
    "province_location_name":"重庆市",
    "pay_time":"2018-09-11 11:56:48",
    "pay_datekey":"20180911",
    "pay_timestamp":"1536638208000",
    "pay_time_5min":"2018-09-11 11:55:00",
    "pay_time_30min":"2018-09-11 11:30:00",
    "pay_time_1hour":"2018-09-11 11:00:00"
}
```

### app.hbdata_hotel_order_pay_unit

```
{
    "total_buy_price":"114.3",
    "apt_id":"728102720",
    "total_room_night_num":"1",
    "create_time_30min":"2018-09-11 11:30:00",
    "total_order_profit":"12.700000000000003",
    "create_timestamp":"1536638123000",
    "create_time_5min":"2018-09-11 11:55:00",
    "order_id":"883694009",
    "total_order_amount":"127.0",
    "biz_day":"2018-09-11 00:00:00",
    "create_datekey":"20180911",
    "id":"841081137",
    "buy_price":"114.3",
    "order_amount":"127.0",
    "order_key":"2002883694009",
    "create_time_1hour":"2018-09-11 11:00:00",
    "create_time":"2018-09-11 11:55:23",
    "order_profit":"12.700000000000003",
    "room_night_num":"1"
}
```
### 选取部分字段
"OrderPayTable"     ------ "order_key, user_id, rowtime.rowtime" （这个rowtime取自pay_timestamp字段）
"OrderPayUnitTable" ------ "order_key, id, order_amount, order_profit, rowtime.rowtime" （这个rowtime取自create_time字段）



## 查询语句

```
SELECT t1.order_key, SUM(t2.order_amount), SUM(t2.order_profit)
	, TUMBLE_END(t1.rowtime, INTERVAL '10' SECOND)
FROM OrderPayTable t1
	LEFT JOIN OrderPayUnitTable t2
	ON t1.order_key = t2.order_key
		AND t1.rowtime BETWEEN t2.rowtime - INTERVAL '5' SECOND AND t2.rowtime + INTERVAL '5' SECOND
GROUP BY TUMBLE(t1.rowtime, INTERVAL '10' SECOND), t1.order_key
```

##  SQL解析与查询优化

### Abstract Syntax Tree
```
== Abstract Syntax Tree ==
LogicalProject(order_key=[$1], EXPR$1=[$2], EXPR$2=[$3], EXPR$3=[TUMBLE_END($0)])
  LogicalAggregate(group=[{0, 1}], EXPR$1=[SUM($2)], EXPR$2=[SUM($3)])
    LogicalProject($f0=[TUMBLE($3, 10000)], order_key=[$0], order_amount=[$6], order_profit=[$7])
      LogicalJoin(condition=[AND(=($0, $4), >=($3, -($9, 5000)), <=($3, DATETIME_PLUS($9, 5000)))], joinType=[left])
        LogicalTableScan(table=[[OrderPayTable]])
        LogicalTableScan(table=[[OrderPayUnitTable]])
```

### Optimized Logical Plan
```
== Optimized Logical Plan ==
DataStreamCalc(select=[order_key, EXPR$1, EXPR$2, w$end AS EXPR$3])
  DataStreamGroupWindowAggregate(groupBy=[order_key], window=[TumblingGroupWindow('w$, 'rowtime, 10000.millis)], select=[order_key, SUM(order_amount) AS EXPR$1, SUM(order_profit) AS EXPR$2, start('w$) AS w$start, end('w$) AS w$end, rowtime('w$) AS w$rowtime, proctime('w$) AS w$proctime])
    DataStreamCalc(select=[rowtime, order_key, order_amount, order_profit])
      DataStreamWindowJoin(where=[AND(=(order_key, order_key0), >=(rowtime, -(rowtime0, 5000)), <=(rowtime, DATETIME_PLUS(rowtime0, 5000)))], join=[order_key, rowtime, order_key0, order_amount, order_profit, rowtime0], joinType=[LeftOuterJoin])
        DataStreamCalc(select=[order_key, rowtime])
          DataStreamScan(table=[[OrderPayTable]])
        DataStreamCalc(select=[order_key, order_amount, order_profit, rowtime])
          DataStreamScan(table=[[OrderPayUnitTable]])

```

### Physical Execution Plan
```
== Physical Execution Plan ==
Stage 1 : Data Source
	content : collect elements with CollectionInputFormat

	Stage 2 : Operator
		content : Flat Map
		ship_strategy : FORWARD

		Stage 3 : Operator
			content : Timestamps/Watermarks
			ship_strategy : FORWARD

Stage 4 : Data Source
	content : collect elements with CollectionInputFormat

	Stage 5 : Operator
		content : Flat Map
		ship_strategy : FORWARD

		Stage 6 : Operator
			content : Timestamps/Watermarks
			ship_strategy : FORWARD

			Stage 7 : Operator
				content : from: (order_key, user_id, ts, rowtime)
				ship_strategy : FORWARD

				Stage 8 : Operator
					content : select: (order_key, rowtime)
					ship_strategy : FORWARD

					Stage 9 : Operator
						content : from: (order_key, id, order_amount, order_profit, timestamp, rowtime)
						ship_strategy : FORWARD

						Stage 10 : Operator
							content : select: (order_key, order_amount, order_profit, rowtime)
							ship_strategy : FORWARD

							Stage 13 : Operator
								content : where: (AND(=(order_key, order_key0), >=(rowtime, -(rowtime0, 5000)), <=(rowtime, DATETIME_PLUS(rowtime0, 5000)))), join: (order_key, rowtime, order_key0, order_amount, order_profit, rowtime0)
								ship_strategy : HASH

								Stage 14 : Operator
									content : select: (rowtime, order_key, order_amount, order_profit)
									ship_strategy : FORWARD

									Stage 15 : Operator
										content : time attribute: (rowtime)
										ship_strategy : FORWARD

										Stage 17 : Operator
											content : groupBy: (order_key), window: (TumblingGroupWindow('w$, 'rowtime, 10000.millis)), select: (order_key, SUM(order_amount) AS EXPR$1, SUM(order_profit) AS EXPR$2, start('w$) AS w$start, end('w$) AS w$end, rowtime('w$) AS w$rowtime, proctime('w$) AS w$proctime)
											ship_strategy : HASH

											Stage 18 : Operator
												content : select: (order_key, EXPR$1, EXPR$2, w$end AS EXPR$3)
												ship_strategy : FORWARD
```
