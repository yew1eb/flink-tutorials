## WindowOperator

WindowOperator算子基于WindowAssigner和Trigger实现window聚合的逻辑

When an element arrives it gets assigned a key using a {@link KeySelector} and it gets assigned to zero or more windows using a {@link WindowAssigner}. Based on this, the element is put into panes. A pane is the bucket of elements that have the same key and same {@code Window}. An element can be in multiple panes if it was assigned to multiple windows by the {@code WindowAssigner}.

Each pane gets its own instance of the provided {@code Trigger}. This trigger determines when the contents of the pane should be processed to emit results. When a trigger fires, the given {@link InternalWindowFunction} is invoked to produce the results that are emitted for the pane to which the {@code Trigger} belongs.

```
	protected final WindowAssigner<? super IN, W> windowAssigner;

	private final KeySelector<IN, K> keySelector;

	private final Trigger<? super IN, ? super W> trigger;

	private final StateDescriptor<? extends AppendingState<IN, ACC>, ?> windowStateDescriptor;

	/** For serializing the key in checkpoints. */
	protected final TypeSerializer<K> keySerializer;

	/** For serializing the window in checkpoints. */
	protected final TypeSerializer<W> windowSerializer;

	/**
	 * The allowed lateness for elements. This is used for:
	 * <ul>
	 *     <li>Deciding if an element should be dropped from a window due to lateness.
	 *     <li>Clearing the state of a window if the system time passes the
	 *         {@code window.maxTimestamp + allowedLateness} landmark.
	 * </ul>
	 */
	protected final long allowedLateness;

	/**
	 * {@link OutputTag} to use for late arriving events. Elements for which
	 * {@code window.maxTimestamp + allowedLateness} is smaller than the current watermark will
	 * be emitted to this.
	 */
	protected final OutputTag<IN> lateDataOutputTag;

	private static final  String LATE_ELEMENTS_DROPPED_METRIC_NAME = "numLateRecordsDropped";

	protected transient Counter numLateRecordsDropped;

	// ------------------------------------------------------------------------
	// State that is not checkpointed
	// ------------------------------------------------------------------------

	/** The state in which the window contents is stored. Each window is a namespace */
	private transient InternalAppendingState<K, W, IN, ACC, ACC> windowState;

	/**
	 * The {@link #windowState}, typed to merging state for merging windows.
	 * Null if the window state is not mergeable.
	 */
	private transient InternalMergingState<K, W, IN, ACC, ACC> windowMergingState;

	/** The state that holds the merging window metadata (the sets that describe what is merged). */
	private transient InternalListState<K, VoidNamespace, Tuple2<W, W>> mergingSetsState;

	/**
	 * This is given to the {@code InternalWindowFunction} for emitting elements with a given
	 * timestamp.
	 */
	protected transient TimestampedCollector<OUT> timestampedCollector;

	protected transient Context triggerContext = new Context(null, null);

	protected transient WindowContext processContext;

	protected transient WindowAssigner.WindowAssignerContext windowAssignerContext;

	// ------------------------------------------------------------------------
	// State that needs to be checkpointed
	// ------------------------------------------------------------------------

	protected transient InternalTimerService<W> internalTimerService;
```

