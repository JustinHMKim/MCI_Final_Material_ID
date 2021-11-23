from matplotlib import axes
import pandas as pd
import matplotlib.pyplot as plt
import numpy as np

filename = "vibecheck.csv"


def plot():
    df = pd.read_csv(
        filename, names=["ranging Tstart", "acc timestamp", "acc z", "signal received"]
    )

    colors = ["blue", "red"]
    legends = ["false", "true"]
    grouped = df.groupby("signal received")
    for key, group in grouped:
        group.plot(
            ax=plt.gca(), kind="line", x="acc timestamp", y="acc z", color=colors[key]
        )
    plt.show()


if __name__ == "__main__":
    plot()
